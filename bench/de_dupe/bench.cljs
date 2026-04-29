(ns de-dupe.bench
  (:require [de-dupe.core :as dd]))

(enable-console-print!)

(defn random-map
  []
  (let [constant-list (doall (range 100))]
   (into [] (take 100 (repeatedly (fn []
                       {:non-cached (rand-int 100)
                        :long-list constant-list}))))))


; (print (random-map))

(def large (doall (take 10 (repeatedly random-map))))
(def raw-size (count (prn-str large)))

(defn report-cache
  [label cache]
  (let [compressed-size (count (prn-str cache))
        ratio (/ compressed-size raw-size)]
    (print label "entries" (count cache))
    (print label "length of de-duped string representation" compressed-size)
    (print label "compressed/raw ratio" ratio)))

(print "length of string representation" raw-size)

(print "******* about to create-cache *******")
(time
  (def cache (dd/de-dupe large)))
(print "******* finished create-cache ******")
(report-cache "de-dupe" cache)


(print "******* about to create-eq-cache *******")
(time
  (def eq-cache (dd/de-dupe-eq large)))
(print "******* finished create-eq-cache ******")
(report-cache "de-dupe-eq" eq-cache)

(print "******* about to decompress *******")
(time
  (def round-tripped (dd/expand cache)))
(print "******* finished decompress ******")
