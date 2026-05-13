(ns de-dupe.core
  #?(:clj (:import [java.util HashMap])))

;; ## Platform note
;;
;; This namespace runs on both JVM Clojure and ClojureScript. The
;; algorithm is platform-agnostic; the only platform-specific bit is
;; the mutable hash-bucket store used during compression. On CLJS we
;; use `js/Map`; on JVM we use `java.util.HashMap`. The shape of the
;; method calls (`.get`, `.set`/`.put`) is the only thing that varies,
;; and is isolated to the `bucket-get` / `bucket-set!` helpers below.

(def cache-element-ns "de-dupe.cache")

;; ---- Mutable hash-bucket store (platform-specific) -------------------------

(defn ^:private new-bucket-store
  "Create an empty mutable hash→bucket map."
  []
  #?(:cljs (js/Map.)
     :clj  (HashMap.)))

(defn ^:private bucket-get
  "Read the bucket associated with `hash` from `store`, returning nil if absent."
  [store hash]
  #?(:cljs (.get store hash)
     :clj  (.get ^java.util.Map store hash)))

(defn ^:private bucket-set!
  "Associate `hash` → `bucket` in the mutable store. Returns the store."
  [store hash bucket]
  #?(:cljs (.set store hash bucket)
     :clj  (.put ^java.util.Map store hash bucket))
  store)

;; ---- Protocol checks (platform-specific) -----------------------------------

(defn ^:private map-entry?*
  [form]
  #?(:cljs (satisfies? IMapEntry form)
     :clj  (instance? clojure.lang.IMapEntry form)))

(defn ^:private record?*
  [form]
  #?(:cljs (satisfies? IRecord form)
     :clj  (instance? clojure.lang.IRecord form)))

;; ---- side-walk / cache traversal -------------------------------------------

(defn side-walk
  "Traverses form, an arbitrary data structure.  inner and outer are
  functions.  Applies inner to each element of form, building up a
  data structure of the same type, then applies outer to the result.
  Recognizes all Clojure data structures. Consumes seqs as with doall."
  {:added "1.1"}
  [inner outer form]
  (cond
    (list? form) (outer form (apply list (doall (map inner form))))
    (map-entry?* form) (outer form (vec (doall (map inner form))))
    (seq? form) (outer form (doall (map inner form)))
    (record?* form) (outer form (reduce (fn [r x] (conj r (inner x))) form form))
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
                (map-entry?* value) (vec (map expand-value value))
                (seq? value) (doall (map expand-value value))
                (record?* value) (reduce (fn [r x] (conj r (expand-value x))) value value)
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

;; cache-id-counter: a per-compression counter. Originally a top-level
;; def with `set!` (CLJS-only); on JVM `set!` of a root binding is not
;; supported, so we use an atom uniformly. Each `create-cache-internal`
;; call resets it to 1.
(def cache-id-counter (atom 1))

(defn next-cache-id! []
  (let [cache-id (make-cache-element @cache-id-counter)]
    (swap! cache-id-counter inc)
    cache-id))

(defn find-cache-id
  [bucket element equivalent?]
  (some (fn [[cached-element cache-id]]
          (when (equivalent? cached-element element)
            cache-id))
        bucket))

(defn check-in-cache
  [element values-store hash-fn equivalent?]
  (let [hash   (hash-fn element)
        bucket (or (bucket-get values-store hash) [])]
    (if-let [cache-id (find-cache-id bucket element equivalent?)]
      cache-id
      (let [cache-id (next-cache-id!)]
        (bucket-set! values-store hash (conj bucket [element cache-id]))
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
  [values-store hash-fn equivalent? element]
  (let [hash   (hash-fn element)
        bucket (or (bucket-get values-store hash) [])]
    (if-let [entry (find-count-entry bucket element equivalent?)]
      (bucket-set! values-store hash (mapv (fn [bucket-entry]
                                             (if (identical? bucket-entry entry)
                                               (inc-count-entry bucket-entry)
                                               bucket-entry))
                                           bucket))
      (bucket-set! values-store hash (conj bucket {:element element :count 1})))))

(defn repeated-cacheable?
  [values-store hash-fn equivalent? element]
  (let [hash   (hash-fn element)
        bucket (or (bucket-get values-store hash) [])]
    (boolean
      (some (fn [{candidate :element :keys [count]}]
              (and (< 1 count)
                   (equivalent? element candidate)))
            bucket))))

(defn count-cacheable-elements
  [form hash-fn equivalent?]
  (let [values-store (new-bucket-store)]
    (side-prewalk (fn [element]
                    (when (and (not (identical? element form))
                               (cachable? element))
                      (count-cacheable-element! values-store hash-fn equivalent? element))
                    element)
                  (fn [_org-element element] element)
                  form)
    values-store))

(defn create-cache-internal
  ([form]
   (create-cache-internal form identity identical?))
  ([form hash-fn equivalent?]
   (reset! cache-id-counter 1)
   (let [compressed-cache (atom {})
         candidate-counts (count-cacheable-elements form hash-fn equivalent?)
         values-store    (new-bucket-store)
         process-element (fn [element]
                           ; don't cache cache elements or [key value] pairs
                           (if (or (identical? element form)
                                   (not (cachable? element))
                                   (not (repeated-cacheable? candidate-counts hash-fn equivalent? element)))
                             element
                             (check-in-cache element values-store hash-fn equivalent?)))
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
     (swap! compressed-cache assoc (make-cache-element 0) cache-0)
     [(make-cache-element 0)
      @compressed-cache
      values-store])))

(defn de-dupe
  "API create an efficient representation for serialization of (immutable persistent)
   data structures with a lot of structural sharing (uses identical? for comparison"
  [form]
  (let [[_message cache _values] (create-cache-internal form)]
    cache))

(defn de-dupe-eq
  "API create an efficient representation for serialization of
   data structures with a lot of shared data (uses = for comparison)"
  [form]
  (let [[_message cache _values] (create-cache-internal form hash =)]
    cache))
