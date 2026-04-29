(defproject de-dupe "0.2.2"
  :description "Creates an efficient representation for serialization of (immutable persistent) data structures 
               with a lot of structural sharing (objects that are identical and referred to in different 
               places in the data)"
  :url "https://github.com/day8/de-dupe"
  :license      {:name "MIT"}
  :scm {:name "git"
         :url "https://github.com/day8/de-dupe"}
  
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [org.clojure/clojurescript "1.11.132"]]
  
  :source-paths ["src"]
  
  :test-paths ["test"]
  
  :clean-targets ["out" "out-adv" "target"]
  
  :profiles {:test {:source-paths ["test"]}}

  :aliases {"test" ["with-profile" "+test" "run" "-m" "de-dupe.test-runner"]})
