(defproject day8/de-dupe "0.3.0"
  :description "Creates an efficient representation for serialization of (immutable persistent) data structures
                with a lot of structural sharing (objects that are identical and referred to in different
                places in the data). Round-trips preserve identical? across the wire."
  :url "https://github.com/day8/de-dupe"
  :license {:name "MIT"
            :url  "https://github.com/day8/de-dupe/blob/main/licence.txt"}
  :scm {:name "git"
        :url  "https://github.com/day8/de-dupe"}

  :dependencies [[org.clojure/clojure       "1.12.4"  :scope "provided"]
                 [org.clojure/clojurescript "1.11.132" :scope "provided"]]

  :source-paths ["src"]
  :test-paths   ["test"]
  :clean-targets ["out" "out-adv" "target"]

  :profiles {:test {:source-paths ["test"]}}

  :aliases {"test" ["with-profile" "+test" "run" "-m" "de-dupe.test-runner"]}

  :deploy-repositories [["clojars" {:url           "https://clojars.org/repo"
                                    :username      :env/CLOJARS_USERNAME
                                    :password      :env/CLOJARS_PASSWORD
                                    :sign-releases false}]])
