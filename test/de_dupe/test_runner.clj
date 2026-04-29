(ns de-dupe.test-runner
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as str]))

(defn run-command! [& command]
  (let [{:keys [exit out err]} (apply sh command)]
    (when-not (str/blank? out)
      (print out))
    (when-not (str/blank? err)
      (binding [*out* *err*]
        (print err)))
    (when-not (zero? exit)
      (System/exit exit))))

(defn -main [& _args]
  (run-command! "clojure" "-M:compile-test")
  (run-command! "node" "target/test/de-dupe-test.js")
  (flush)
  (binding [*out* *err*]
    (flush))
  (System/exit 0))
