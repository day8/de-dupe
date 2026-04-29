(ns de-dupe.core)

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
  (cond
    (is-cache-element? value) true
    (coll? value) (boolean (some contains-compressed-elements? value))
    :else false))

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
  (let [expanded (atom {})]
    (letfn [(expand-value [value]
              (cond
                (is-cache-element? value) (expand-entry value)
                (list? value) (apply list (map expand-value value))
                (satisfies? IMapEntry value) (vec (map expand-value value))
                (seq? value) (doall (map expand-value value))
                (satisfies? IRecord value) (reduce (fn [r x] (conj r (expand-value x))) value value)
                (coll? value) (into (empty value) (map expand-value value))
                :else value))
            (expand-entry [cache-id]
              (if (contains? @expanded cache-id)
                (@expanded cache-id)
                (let [value (get cache cache-id)
                      expanded-value (expand-value value)]
                  (swap! expanded assoc cache-id expanded-value)
                  expanded-value)))]
      (into {}
            (for [cache-id (keys cache)]
              [cache-id (expand-entry cache-id)])))))

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

(defn find-count-entry
  [bucket element equivalent?]
  (some (fn [entry]
          (when (equivalent? (:element entry) element)
            entry))
        bucket))

(defn inc-count-entry
  [entry]
  (update entry :count inc))

(defn count-cacheable-element!
  [js-values hash-fn equivalent? element]
  (let [hash (hash-fn element)
        bucket (or (.get js-values hash) [])]
    (if-let [entry (find-count-entry bucket element equivalent?)]
      (.set js-values hash (mapv (fn [bucket-entry]
                                   (if (identical? bucket-entry entry)
                                     (inc-count-entry bucket-entry)
                                     bucket-entry))
                                 bucket))
      (.set js-values hash (conj bucket {:element element :count 1})))))

(defn repeated-cacheable?
  [js-values hash-fn equivalent? element]
  (let [hash (hash-fn element)
        bucket (or (.get js-values hash) [])]
    (boolean
      (some (fn [{candidate :element :keys [count]}]
              (and (< 1 count)
                   (equivalent? element candidate)))
            bucket))))

(defn count-cacheable-elements
  [form hash-fn equivalent?]
  (let [js-values (js/Map.)]
    (side-prewalk (fn [element]
                    (when (and (not (identical? element form))
                               (cachable? element))
                      (count-cacheable-element! js-values hash-fn equivalent? element))
                    element)
                  (fn [_org-element element] element)
                  form)
    js-values))

(defn create-cache-internal
  ([form]
   (create-cache-internal form identity identical?))
  ([form hash-fn equivalent?]
   (set! cache-id-counter 1)
   (let [compressed-cache (atom {})
         candidate-counts (count-cacheable-elements form hash-fn equivalent?)
         js-values (js/Map.)
         process-element (fn [element]
                           ; don't cache cache elements or [key value] pairs
                           (if (or (identical? element form)
                                   (not (cachable? element))
                                   (not (repeated-cacheable? candidate-counts hash-fn equivalent? element)))
                             element
                             (check-in-cache element js-values hash-fn equivalent?)))
         outer-fn      (fn [org-element element]
                         (if (and (cachable? org-element)
                                  (not (identical? org-element form)))
                           (let [id (:cache-id (meta org-element))]
                             (if (not (nil? id))
                               (do
                                 (swap! compressed-cache assoc id element)
                                 id)
                               element))
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
