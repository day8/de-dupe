(ns build
  "Build tasks for day8/de-dupe.

  Usage:
    clojure -T:build clean
    clojure -T:build jar
    clojure -T:build install
    clojure -T:build deploy   ; needs CLOJARS_USERNAME + CLOJARS_PASSWORD"
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'day8/de-dupe)
(def version "0.3.0")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn- basis []
  (b/create-basis {:project "deps.edn"}))

(defn- inject-provided-scope
  "Post-process the emitted pom.xml to add <scope>provided</scope> to the
   org.clojure/clojure and org.clojure/clojurescript dependency entries.

   tools.build's write-pom (as of v0.10.13) ignores :mvn/scope in deps.edn —
   only :exclusions and :optional are honored. This restores the provided-scope
   semantics the legacy project.clj had on these deps, so consumers don't
   transitively pull a specific Clojure/CLJS version from this library."
  [pom-path]
  (let [pom (slurp pom-path)
        ;; Match a <dependency> block whose groupId/artifactId is one of the
        ;; targets, then inject <scope>provided</scope> before </dependency>.
        ;; The block has no existing <scope> tag (tools.build doesn't emit one),
        ;; so we simply insert before the closing tag.
        target-artifacts #{"clojure" "clojurescript"}
        dep-re #"(?s)(    <dependency>\s+<groupId>org\.clojure</groupId>\s+<artifactId>([^<]+)</artifactId>\s+<version>[^<]+</version>\s+)(</dependency>)"
        pom' (str/replace pom dep-re
                          (fn [[_ head artifact tail]]
                            (if (target-artifacts artifact)
                              (str head "  <scope>provided</scope>\n    " tail)
                              (str head tail))))]
    (when (= pom pom')
      (throw (ex-info "inject-provided-scope: no Clojure/CLJS deps matched — pom format may have changed"
                      {:pom-path pom-path})))
    (spit pom-path pom')))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     (basis)
                :src-dirs  ["src"]
                :scm       {:tag                 (str "v" version)
                            :url                 "https://github.com/day8/de-dupe"
                            :connection          "scm:git:git://github.com/day8/de-dupe.git"
                            :developerConnection "scm:git:ssh://git@github.com/day8/de-dupe.git"}
                :pom-data  [[:licenses
                             [:license
                              [:name "MIT License"]
                              [:url "https://github.com/day8/de-dupe/blob/main/licence.txt"]
                              [:distribution "repo"]]]]})
  ;; tools.build write-pom drops :mvn/scope — restore <scope>provided</scope>
  ;; on the Clojure + CLJS deps before the jar is built.
  (inject-provided-scope (b/pom-path {:lib lib :class-dir class-dir}))
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file  jar-file})
  (println "Wrote" jar-file))

(defn install [_]
  (jar nil)
  (b/install {:basis     (basis)
              :lib       lib
              :version   version
              :jar-file  jar-file
              :class-dir class-dir})
  (println "Installed" lib version "to local Maven repo"))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact  jar-file
              :pom-file  (b/pom-path {:lib       lib
                                      :class-dir class-dir})})
  (println "Deployed" lib version "to Clojars"))
