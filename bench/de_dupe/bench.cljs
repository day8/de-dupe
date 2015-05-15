;; run this with phantomjs

(ns de-dupe.test.core
  (:require [de-dupe.core :as dd]
            [clojure.walk :refer [prewalk]]))

(enable-console-print!)

(defn random-map
  []
  (let [constant-list (doall (range 100))]
   (into [] (take 100 (repeatedly (fn []
                       {:non-cached (rand-int 100)
                        :long-list constant-list}))))))


; (print (random-map))

(def large (doall (take 10 (repeatedly random-map))))
(print "length of string representation" (count (prn-str large)))

(print "******* about to create-cache *******")
(time
  (def cache (dd/create-cache large)))
(print "******* finished create-cache ******")
(print "length of de-duped string representation" (count (prn-str cache)))


(print "******* about to create-eq-cache *******")
(time
  (def eq-cache (dd/create-eq-cache large)))
(print "******* finished create-eq-cache ******")
(print "length of de-duped eq string representation" (count (prn-str eq-cache)))

(print "******* about to decompress *******")
(time
  (def round-tripped (.decompress cache)))
(print "******* finished decompress ******")
