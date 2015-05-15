(defproject de-dupe "0.2.1"
  :description "Creates an efficient representation for serialization of (immutable persistent) data structures 
               with a lot of structural sharing (objects that are identical and referred to in different 
               places in the data)"
  :url "https://github.com/Day8/cljs-structural-caching.git"
  :license      {:name "MIT"}
  :scm {:name "git"
         :url "https://github.com/Day8/cljs-structural-caching"}
  
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3211"]
                 [com.cognitect/transit-cljs "0.8.205"]]
  
  :node-dependencies [[source-map-support "0.2.8"]]
  
  :plugins [[lein-cljsbuild "1.0.4"]
            [lein-npm "0.4.0"]
            [com.cemerick/clojurescript.test "0.3.3"]]
  
  :source-paths ["src"]
  
  :test-paths ["test"]
  
  :clean-targets ["out" "out-adv"]
  
  :cljsbuild {
              :builds [{:id "dev"
                        :source-paths ["src"]
                        :compiler {
                                   :main structural-caching.core
                                   :output-to "out/de-dupe.js"
                                   :output-dir "out"
                                   :optimizations :none
                                   :cache-analysis true
                                   :source-map true}}
                       {:id "test-dev"
                        :source-paths ["src" "test/de_dupe/test"] 
                        :compiler {
                                   :output-to "out/de-dupe.test-dev.js"
                                   :output-dir "out/out-dev-test"
                                   :optimizations :none
                                   :source-map true}}
                       {:id "bench"
                        :source-paths ["src" "bench"]
                        :compiler {
                                   :output-to "out/dd_bench.js"
                                   :output-dir "out/out-bench"
                                   :optimizations :none
                                   :pretty-print true
                                   :source-map true
                                   :externs ["resources/node_externs.js"]}}
                       {:id "test"
                        :source-paths ["src" "test/de_dupe/test"] 
                        :compiler {
                                   :output-to "out/de-dupe.test.js"
                                   :output-dir "out/out-test"
                                   :optimizations :simple}}
                       {:id "release"
                        :source-paths ["src"]
                        :compiler {
                                   :main structural-caching.core
                                   :output-to "out-adv/de-dupe.min.js"
                                   :output-dir "out-adv"
                                   :optimizations :advanced
                                   :pretty-print false}}]
              :test-commands {"unit-tests" 
                              ["xvfb-run" "-a" "slimerjs" :runner "out/de-dupe.test.js"]}}
  
  :aliases {"test" ["do" "clean," "cljsbuild" "test" "unit-tests"]})
