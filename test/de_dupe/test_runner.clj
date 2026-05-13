(ns de-dupe.test-runner
  "Top-level test runner for de-dupe. Drives both the CLJS test suite
  (compiled to JS and run on Node) and the JVM Clojure test suite. The
  source under test (`de-dupe.core`) is .cljc and the test source
  (`de-dupe.test.core`) is also .cljc; we exercise both targets so
  any platform-conditional drift is caught."
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.test :as t]))

(defn run-command! [& command]
  (let [{:keys [exit out err]} (apply sh command)]
    (when-not (str/blank? out)
      (print out))
    (when-not (str/blank? err)
      (binding [*out* *err*]
        (print err)))
    (when-not (zero? exit)
      (System/exit exit))))

(defn run-cljs-tests! []
  (println "Running CLJS tests (compile + Node)…")
  (run-command! "clojure" "-M:compile-test")
  (run-command! "node" "target/test/de-dupe-test.js"))

(defn run-clj-tests! []
  (println "Running JVM (CLJ) tests…")
  (require 'de-dupe.test.core)
  (let [summary (t/run-tests 'de-dupe.test.core)]
    (flush)
    (when (or (pos? (:fail summary 0))
              (pos? (:error summary 0)))
      (System/exit 1))))

(defn -main [& _args]
  (run-cljs-tests!)
  (run-clj-tests!)
  (flush)
  (binding [*out* *err*]
    (flush))
  (System/exit 0))
