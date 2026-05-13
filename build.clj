(ns build
  "Build tasks for day8/de-dupe.

  Usage:
    clojure -T:build clean
    clojure -T:build jar
    clojure -T:build install
    clojure -T:build deploy   ; needs CLOJARS_USERNAME + CLOJARS_PASSWORD"
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'day8/de-dupe)
(def version "0.3.0")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn- basis []
  (b/create-basis {:project "deps.edn"}))

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
