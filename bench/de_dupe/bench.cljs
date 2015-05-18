(ns de-dupe.test.core
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
(print "length of string representation" (count (prn-str large)))

(print "******* about to create-cache *******")
(time
  (def cache (dd/de-dupe large)))
(print "******* finished create-cache ******")
(print "length of de-duped string representation" (count (prn-str cache)))


(print "******* about to create-eq-cache *******")
(time
  (def eq-cache (dd/de-dupe-eq large)))
(print "******* finished create-eq-cache ******")
(print "length of de-duped eq string representation" (count (prn-str eq-cache)))

(print "******* about to decompress *******")
(time
  (def round-tripped (expand cache)))
(print "******* finished decompress ******")
