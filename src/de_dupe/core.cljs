(ns de-dupe.core
  (:require [clojure.walk :refer [postwalk-replace]]))

;; (repl/connect "http://localhost:9000/repl")

(def cache-element-ns "de-dupe.cache")

(defn side-walk
  "Traverses form, an arbitrary data structure.  inner and outer are
  functions.  Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall."
  
  {:added "1.1"}
  [inner outer form]
  (cond
    (list? form) (outer form (apply list (doall (map inner form))))
    (satisfies? IMapEntry form) (outer form (vec (doall (map inner form))))
    (seq? form) (outer form (doall (map inner form)))
    (satisfies? IRecord form) (outer form (reduce (fn [r x] (conj r (inner x))) form form))
    (coll? form) (outer form (into (empty form) 
                                   (doall (map inner form))))
    :else (outer form form)))

(defn is-cache-element?
  "tests is an item is a cache tag"
  [element]
  (and (symbol? element)
       (or (= cache-element-ns (namespace element))
           (some? (::cache (meta element))))))

(defn make-cache-element
  [id]
  (symbol cache-element-ns (str "cache-" id)))

(defn map-from-seq
  [seq]
  (into {} (for [[key value] seq] 
             [key value])))

(defn contains-compressed-elements?
  [value]
  (if (and (coll? value)
           (some is-cache-element? 
                 (flatten (seq value))))
    true
    false))

(defn partition-decompressed-elements
  [cache]
  (let [partition (group-by (fn [[key value]]
                              (if (contains-compressed-elements? value)
                                :compressed
                                :decompressed)) (seq cache))]
    [(map-from-seq (:decompressed partition)) 
     (map-from-seq (:compressed partition))]))

(defn contains-only-keys?
  "looks at t cache values and sees if it only contains keys in keys"
  [cache keys]
  (every? #(if (is-cache-element? %)
             (some #{%} keys)
             true)
          (flatten (seq (last cache)))))

(defn decompress-cache
  [cache]
  (loop [decompressed-cache {}
         cache cache]
    (let [[new-decompressed cache] (partition-decompressed-elements cache)
          decompressed-cache (merge decompressed-cache new-decompressed)]
      (if (empty? cache)
        decompressed-cache
        (let [new-cache
              (into {}
                    (for [[key value] cache]
                      (let [decompressed-value
                            (postwalk-replace decompressed-cache value)
                            value (if (contains-compressed-elements?
                                        decompressed-value)
                                    value
                                    decompressed-value)]
                        [key value])))]
          (recur decompressed-cache new-cache))))))

(defn expand
  "This is the API function to take a cache of elements and expand them"
  [cache]
  ((decompress-cache cache) (make-cache-element 0)))

(def cache-id-counter 1)

(defn next-cache-id! []
  (let [cache-id (make-cache-element cache-id-counter)]
    (set! cache-id-counter (inc cache-id-counter))
    cache-id))

(defn find-cache-id
  [bucket element equivalent?]
  (some (fn [[cached-element cache-id]]
          (when (equivalent? cached-element element)
            cache-id))
        bucket))

(defn check-in-cache
  [element js-values hash-fn equivalent?]
  (let [hash (hash-fn element)
        bucket (or (.get js-values hash) [])]
    (if-let [cache-id (find-cache-id bucket element equivalent?)]
      cache-id
      (let [cache-id (next-cache-id!)]
        (.set js-values hash (conj bucket [element cache-id]))
        (with-meta element {:cache-id cache-id})))))

(defn side-prewalk
  [inner outer form]
  (side-walk (partial side-prewalk inner outer) 
             outer 
             (inner form)))

(defn cachable?
  "Determines if we can cache an element"
  [element]
  (and
    (not 
      (or (and (vector? element)
               (= 2 (count element)))
          (number? element)
          (keyword? element)
          (string? element)))
    (or  
      (list? element)
      (seq? element)
      (coll? element))))

(defn create-cache-internal
  ([form]
   (create-cache-internal form identity identical?))
  ([form hash-fn equivalent?]
   (set! cache-id-counter 1)
   (let [compressed-cache (atom {})
         js-values (js/Map.)        
         process-element (fn [element]
                           ; don't cache cache elements or [key value] pairs
                           (if (or (identical? element form)
                                   (not (cachable? element)))
                             element
                             (check-in-cache element js-values hash-fn equivalent?)))
         outer-fn      (fn [org-element element]
                         (if (and (cachable? org-element)
                                  (not (identical? org-element form)))
                           (let [id (:cache-id (meta org-element))]
                             (when (not (nil? id))
                               (swap! compressed-cache assoc id element)
                               id))
                           element))
         cache-0       (side-prewalk process-element outer-fn form)]
     ; (print "compressed-cache" compressed-cache)
     ; (print "cache-0" cache-0)
     (swap! compressed-cache assoc (make-cache-element 0) cache-0)
     ;(print "The number of unique keys are:" cache-id-counter)
     [(make-cache-element 0)
      @compressed-cache
      js-values])))

(defn de-dupe
  "API create an efficient representation for serialization of (immutable persistent) 
   data structures with a lot of structural sharing (uses identical? for comparison"
  [form]
  (let [[message cache values] (create-cache-internal form)]
    cache))

(defn de-dupe-eq
  "API create an efficient representation for serialization of 
   data structures with a lot of shared data (uses = for comparison)"
  [form]
  (let [[message cache values] (create-cache-internal form hash =)]
    cache))
