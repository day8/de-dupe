(ns de-dupe.test-runner
  "Top-level test runner for de-dupe. Drives both the CLJS test suite
  (compiled to JS and run on Node) and the JVM Clojure test suite. The
  source under test (`de-dupe.core`) is .cljc and the test source
  (`de-dupe.test.core`) is also .cljc; we exercise both targets so
  any platform-conditional drift is caught.

  The runner enforces a **minimum assertion count** per platform to
  catch the silent-failure pattern where compilation succeeds but
  zero tests actually load (e.g. a broken reader-conditional makes
  the test namespace evaluate to nothing). Without this gate, both
  legs could happily exit 0 having executed nothing."
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]
            [clojure.test :as t]))

;; Floor for assertion counts. Bump this whenever you intentionally
;; remove tests; treat a drop below the floor as a silent-failure
;; signal, not a passing build.
(def ^:private min-assertions-per-platform 60)

(def ^:private summary-pattern
  ;; Matches the line clojure.test (and cljs.test) prints, e.g.
  ;;   "Ran 9 tests containing 43 assertions."
  #"Ran (\d+) tests containing (\d+) assertions\.")

(defn ^:private parse-summary
  "Extract [tests assertions] from a clojure.test output blob. Returns
  nil if no summary line was emitted (which itself is a failure signal)."
  [output]
  (when-let [[_ t a] (re-find summary-pattern (or output ""))]
    [(Long/parseLong t) (Long/parseLong a)]))

(defn ^:private die!
  [code & msg]
  (binding [*out* *err*]
    (apply println "test-runner:" msg)
    (flush))
  (System/exit code))

(defn ^:private gate-summary!
  "Enforce the assertion-count floor and detect the silent 'zero tests
  ran' pattern. `label` is for diagnostics."
  [label output]
  (if-let [[tests assertions] (parse-summary output)]
    (do
      (when (zero? tests)
        (die! 2 label "ran 0 tests — likely a load failure"))
      (when (zero? assertions)
        (die! 2 label "ran 0 assertions — likely a load failure"))
      (when (< assertions min-assertions-per-platform)
        (die! 2 label "ran" assertions "assertions (< floor"
              min-assertions-per-platform ") — tests may have been silently dropped"))
      (println (str "  → " label ": " tests " tests / " assertions " assertions ✓")))
    (die! 2 label "produced no clojure.test summary line — runner cannot verify it actually ran")))

(defn ^:private run-and-capture!
  "Run a shell command, stream its stdout/stderr to ours, and also
  return the captured stdout for parsing."
  [& command]
  (let [{:keys [exit out err]} (apply sh command)]
    (when-not (str/blank? out) (print out) (flush))
    (when-not (str/blank? err)
      (binding [*out* *err*] (print err) (flush)))
    (when-not (zero? exit)
      (die! exit "command failed (exit" (str exit "):") (str/join " " command)))
    out))

(defn run-cljs-tests! []
  (println "Running CLJS tests (compile + Node)…")
  (run-and-capture! "clojure" "-M:compile-test")
  (let [out (run-and-capture! "node" "target/test/de-dupe-test.js")]
    (gate-summary! "CLJS" out)))

(defn run-clj-tests! []
  (println "Running JVM (CLJ) tests…")
  (require 'de-dupe.test.core)
  (let [captured (java.io.StringWriter.)
        summary  (binding [t/*test-out* captured]
                   (t/run-tests 'de-dupe.test.core))
        out      (str captured)]
    (print out) (flush)
    (when (or (pos? (:fail summary 0)) (pos? (:error summary 0)))
      (die! 1 "JVM: failures or errors present"))
    (gate-summary! "JVM" out)))

(defn -main [& _args]
  (run-cljs-tests!)
  (run-clj-tests!)
  (println "All test legs passed assertion-floor gate.")
  (flush)
  (binding [*out* *err*] (flush))
  (System/exit 0))
