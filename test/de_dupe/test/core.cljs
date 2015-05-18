(ns de-dupe.test.core
  (:require [cemerick.cljs.test :as test :refer-macros [deftest is 
                                                        run-tests testing]]
            [de-dupe.core :as sc :refer [decompress-cache
                                         make-cache-element
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
             {(make-cache-element 1) hello-vec
              (make-cache-element 0) [(make-cache-element 1)]}))
      (is (= (de-dupe [hello-vec ["hello"]]) 
             {(make-cache-element 1) hello-vec
              (make-cache-element 2) ["hello"]
              (make-cache-element 0) [(make-cache-element 1) 
                                      (make-cache-element 2)]}))
      (is (= (de-dupe-eq [hello-vec ["hello"]]) 
             {(make-cache-element 1) hello-vec
              (make-cache-element 0) [(make-cache-element 1) 
                                      (make-cache-element 1)]}))
      (is (= (de-dupe [hello-vec ["hello"]]) 
             {(make-cache-element 1) hello-vec
              (make-cache-element 2) ["hello"]
              (make-cache-element 0) [(make-cache-element 1) 
                                      (make-cache-element 2)]}))
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

(deftest test-decompress-cache
  (testing "Testing that we can decompress a cache object"
    (is (= (decompress-cache {(make-cache-element 1) "hello" 
                              (make-cache-element 0) 
                              [(make-cache-element 1) (make-cache-element 1)]})
           {(make-cache-element 1) "hello" 
            (make-cache-element 0) 
            ["hello" "hello"]}))))

(defn round-trip [x]
  (expand (de-dupe x)))

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

(run-tests)