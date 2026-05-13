(ns de-dupe.test.core
  (:require
    #?(:clj  [clojure.test :as test :refer [deftest is testing run-tests]]
       :cljs [cljs.test :as test :refer-macros [deftest is run-tests testing]])
    #?(:clj  [clojure.edn :as reader]
       :cljs [cljs.reader :as reader])
    [de-dupe.core :as sc :refer [decompress-cache
                                 make-cache-element
                                 contains-compressed-elements?
                                 create-cache-internal
                                 expand
                                 de-dupe
                                 de-dupe-eq]]))

#?(:cljs (enable-console-print!))

(defn ^:private read-string* [s]
  #?(:clj  (reader/read-string s)
     :cljs (reader/read-string s)))

(deftest test-read-cache
  (testing "Testing reading cached items"
    (let [zero-cache {(make-cache-element 0) "hello"}
          simple-cache {(make-cache-element 0) ["hello"]}]
      (is (= (expand zero-cache) "hello"))
      (is (= (expand simple-cache) ["hello"])))
    (is (= (expand {(make-cache-element 1) "hello"
                    (make-cache-element 0) [(make-cache-element 1) "goodbye"]})
           ["hello" "goodbye"]))
    (is (= (expand {(make-cache-element 2) "hello"
                    (make-cache-element 1) {:a-keyword (make-cache-element 2)}
                    (make-cache-element 0)  [(make-cache-element 1) "goodbye"]})
           [{:a-keyword "hello"} "goodbye"]))))


(deftest test-write-cache
  (testing "Testing that we can create a cache object"
    (is (= (de-dupe "hello") {(make-cache-element 0) "hello"}))
    (is (= (de-dupe ["hello"]) {(make-cache-element 0) ["hello"]}))
    (let [hello-vec ["hello"]]
      (is (= (de-dupe [hello-vec])
             {(make-cache-element 0) [hello-vec]}))
      (is (= (de-dupe [hello-vec ["hello"]])
             {(make-cache-element 0) [hello-vec ["hello"]]}))
      (is (= (de-dupe-eq [hello-vec ["hello"]])
             {(make-cache-element 1) hello-vec
              (make-cache-element 0) [(make-cache-element 1)
                                      (make-cache-element 1)]}))
      (is (= (de-dupe [hello-vec ["hello"]])
             {(make-cache-element 0) [hello-vec ["hello"]]}))
      (is (= (de-dupe [hello-vec hello-vec])
             {(make-cache-element 1) hello-vec
              (make-cache-element 0) [(make-cache-element 1)
                                      (make-cache-element 1)]}))
      (is (= (de-dupe {hello-vec hello-vec})
             {(make-cache-element 1) hello-vec
              (make-cache-element 0) {(make-cache-element 1)
                                      (make-cache-element 1)}})))
    (let [triple-hello ["hello" "hello" "hello"]]
      (is (= (de-dupe [triple-hello triple-hello])
             {(make-cache-element 1) triple-hello
              (make-cache-element 0) [(make-cache-element 1)
                                      (make-cache-element 1)]})))))

(deftest test-repeated-only-extraction
  (testing "Unique child structures stay inline and repeated structures are extracted"
    (let [shared {:shared [1 2 3]}
          value {:unique-a {:a 1}
                 :unique-b {:b 2}
                 :shared-a shared
                 :shared-b shared}
          cache (de-dupe value)]
      (is (= 3 (count cache)))
      (is (= (make-cache-element 1) (get-in cache [(make-cache-element 0) :shared-a])))
      (is (= (make-cache-element 1) (get-in cache [(make-cache-element 0) :shared-b])))
      (is (= (make-cache-element 2) (get-in cache [(make-cache-element 1) :shared])))
      (is (= {:a 1} (get-in cache [(make-cache-element 0) :unique-a])))
      (is (= {:b 2} (get-in cache [(make-cache-element 0) :unique-b]))))))

(deftest test-decompress-cache
  (testing "Testing that we can decompress a cache object"
    (is (= (decompress-cache {(make-cache-element 1) "hello"
                              (make-cache-element 0)
                              [(make-cache-element 1) (make-cache-element 1)]})
           {(make-cache-element 1) "hello"
            (make-cache-element 0)
            ["hello" "hello"]}))))

(deftest test-cache-token-detection
  (testing "Cache-token detection is recursive and does not match normal symbols"
    (is (contains-compressed-elements? {:a [{:b (make-cache-element 1)}]}))
    (is (not (contains-compressed-elements? {:a ['cache-1 'cache-2]})))))

(defn round-trip [x]
  (expand (de-dupe x)))

(defn serialized-round-trip [x]
  (expand (read-string* (pr-str (de-dupe x)))))

(defn round-trip-test [x]
  (= x (round-trip x)))

(defrecord TestRecord [])

(deftest test-round-trip
  (testing "Items can be roundtriped"
    (is (= (expand (de-dupe {"hello" "hello"}))
           {"hello" "hello"}))
    (is (round-trip-test {:a 1 :b 2 :c 3}))
    (is (round-trip-test {:a 1 :b 2 :c [1 2]}))
    (is (round-trip-test {{:a :b} 1 :b 2 :c [1 2 3 4 5]}))
    (is (round-trip-test (->TestRecord)))
    (is (round-trip-test (list* (doall (range 4)))))
    (is (round-trip-test '(0 1 2 3)))))

(deftest test-serialized-round-trip
  (testing "Cache tokens survive printed serialization"
    (let [shared {:a 1 :b [2 3]}
          value {:left shared :right shared}
          after (serialized-round-trip value)]
      (is (= value after))
      (is (identical? (:left after) (:right after)))))
  (testing "User symbols that look like old cache ids are data, not tokens"
    (is (= '[cache-1 cache-2]
           (serialized-round-trip '[cache-1 cache-2])))))

(deftest test-eq-cache-hash-collisions
  (testing "de-dupe-eq logic handles unequal values with the same hash"
    (let [a {:a 1}
          b {:b 2}
          value [a b a b]
          [_ cache _] (create-cache-internal value (constantly 0) =)
          after (expand cache)]
      (is (= value after))
      (is (identical? (nth after 0) (nth after 2)))
      (is (identical? (nth after 1) (nth after 3)))
      (is (not (identical? (nth after 0) (nth after 1)))))))

(deftest identity-check
  (testing "see if structural identity is preserved"
    (let [x {:a 1 :b 2 :c 3}
          x1 {:1 x :2 x :3 x}
          x2 (assoc-in x1 [:3 :a] [x x x])
          x2-after (round-trip x2)
          x3 [x x {:a x}]
          x3-after (round-trip x3)]
      (is (identical? (first x3) (nth x3 1)))
      (is (identical? (first x3-after) (nth x3-after 1)))
      (is (round-trip-test x))
      (is (round-trip-test x1))
      (is (= x2 x2-after))
      (is (identical? (:1 x2-after) (:2 x2-after)))
      (is (identical? (:1 x2-after) (get-in x2-after [:3 :a 0]))))))

;; -- Edge cases --------------------------------------------------------------
;;
;; These are deliberately tiny shapes that have historically tripped walk-
;; based algorithms: nil at the root, empties, single-element collections,
;; and primitives. The library inlines these (they aren't repeated, and
;; primitives aren't `cachable?`), but they MUST round-trip cleanly.

(deftest test-edge-empties-and-primitives
  (testing "nil round-trips"
    (is (round-trip-test nil)))
  (testing "empty collections round-trip"
    (is (round-trip-test []))
    (is (round-trip-test {}))
    (is (round-trip-test #{}))
    (is (round-trip-test '())))
  (testing "single-element collections round-trip"
    (is (round-trip-test [:x]))
    (is (round-trip-test {:a 1}))
    (is (round-trip-test #{:x}))
    (is (round-trip-test '(:x))))
  (testing "primitives round-trip"
    (is (round-trip-test 42))
    (is (round-trip-test 3.14))
    (is (round-trip-test "string"))
    (is (round-trip-test :keyword))
    (is (round-trip-test 'symbol))
    (is (round-trip-test true))
    (is (round-trip-test false))))

(deftest test-set-round-trip
  (testing "sets round-trip, including sets with shared sub-structures"
    (let [shared [1 2 3]
          value #{[shared] [shared shared] [:other]}
          after (round-trip value)]
      (is (= value after))
      (is (set? after)))))

(deftest test-sorted-variants-round-trip
  (testing "sorted-map round-trips and preserves key order"
    (let [sm (sorted-map :a 1 :b 2 :c 3)
          after (round-trip sm)]
      (is (= sm after))
      ;; key order: the algorithm rebuilds collections via `(into (empty form) ...)`
      ;; so sorted-map-ness MUST survive. If this regresses, expand returns a
      ;; plain hash-map and the assertion below fails on CLJS, or `keys` order
      ;; drifts on JVM.
      (is (= (keys sm) (keys after)))))
  (testing "sorted-set round-trips and preserves element order"
    (let [ss (sorted-set 3 1 4 1 5 9 2 6)
          after (round-trip ss)]
      (is (= ss after))
      (is (= (seq ss) (seq after))))))

(deftest test-deeply-nested-round-trip
  (testing "deeply nested vectors survive round-trip"
    ;; 200 levels deep — well past anything reasonable, but shallow enough
    ;; to avoid stack overflow on both runtimes.
    (let [deep (reduce (fn [acc i] [i acc]) :leaf (range 200))]
      (is (round-trip-test deep))))
  (testing "deeply nested maps survive round-trip"
    (let [deep (reduce (fn [acc i] {:k i :down acc}) {:leaf true} (range 100))]
      (is (round-trip-test deep)))))

(deftest test-wide-collection-round-trip
  (testing "wide vector round-trips"
    (is (round-trip-test (vec (range 1000)))))
  (testing "wide map round-trips"
    (is (round-trip-test (into {} (for [i (range 500)] [i (str i)])))))
  (testing "wide set round-trips"
    (is (round-trip-test (set (range 500))))))

(deftest test-platform-parity-shape
  "Same input must produce the same compressed shape on every platform.
   We pin the shape by checking that:
   (a) the cache has a deterministic count,
   (b) cache-0 is the root and references the inner cache-id,
   (c) the inner value is the shared sub-structure verbatim.
   If platform-specific iteration order ever leaks into key assignment,
   one of these assertions trips and we know to investigate."
  (let [shared [1 2 3]
        value  [shared shared shared]
        cache  (de-dupe value)]
    (is (= 2 (count cache)) "two cache entries: root + one shared element")
    (is (= [(make-cache-element 1)
            (make-cache-element 1)
            (make-cache-element 1)]
           (get cache (make-cache-element 0)))
        "root holds three references to the same cache-id")
    (is (= shared (get cache (make-cache-element 1)))
        "the cache-id resolves to the shared sub-structure")))

(deftest test-quoted-and-symbol-forms
  (testing "quoted list shapes round-trip without their elements becoming cache tokens"
    (is (round-trip-test '(a b c)))
    (is (round-trip-test '[a b c]))
    ;; A symbol whose namespace is the cache namespace but whose NAME doesn't
    ;; match the cache-N convention is not a cache element. Conversely, a bare
    ;; `cache-1` symbol (no namespace) is data and survives a round-trip.
    (is (round-trip-test 'cache-1))
    (is (round-trip-test '[cache-1 cache-2 cache-3]))))

;; Cycles are NOT supported. The algorithm walks the value with side-prewalk;
;; a cyclic structure would never terminate. Persistent Clojure collections
;; cannot natively express cycles anyway (you'd need a mutable reference),
;; so this is a non-issue for the intended use case (wire-boundary dedup
;; of immutable values from pair2-mcp and story-mcp).

#?(:cljs
   (defn -main [& _args]
     (let [summary (run-tests 'de-dupe.test.core)]
       (when-not (test/successful? summary)
         (set! js/process.exitCode 1)))))

#?(:cljs (set! *main-cli-fn* -main))
