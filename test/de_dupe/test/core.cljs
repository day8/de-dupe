(ns de-dupe.test.core
  (:require [cljs.reader :as reader]
            [cljs.test :as test :refer-macros [deftest is
                                               run-tests testing]]
            [de-dupe.core :as sc :refer [decompress-cache
                                         make-cache-element
                                         contains-compressed-elements?
                                         create-cache-internal
                                         expand
                                         de-dupe
                                         de-dupe-eq]]))

(enable-console-print!)

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
           [{:a-keyword "hello"} "goodbye"]))
    ))


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
                                                    (make-cache-element 1)]})))
    ))

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
  (expand (reader/read-string (pr-str (de-dupe x)))))

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
    (is (round-trip-test (TestRecord.)))
    (is (round-trip-test (list* (doall (range 4)))))
    (is (round-trip-test '(0 1 2 3)))
    ))

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
      (is (identical? (:1 x2-after) (get-in x2-after [:3 :a 0])))
      )))

(defn -main [& _args]
  (let [summary (run-tests 'de-dupe.test.core)]
    (when-not (test/successful? summary)
      (set! js/process.exitCode 1))))

(set! *main-cli-fn* -main)
