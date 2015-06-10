(ns tourbillon.storage.key-value-test
  (:require [clojure.test :refer :all]
            [tourbillon.storage.key-value :refer :all]))

(def ^:dynamic *store*)

(defn with-local-store [test-fn]
  (let [store (new-kv-store {:type :local
                             :db (atom {}):last-check 122})]
    (.start store)
    (binding [*store* store]
      (test-fn))
    (.stop store)))

(use-fixtures :each with-local-store)

(deftest test-roundtrip 
  (testing "Gets and sets a value at a given key"
    (set-val! *store* :some-key "Some Value")
    (is (= "Some Value" (get-val *store* :some-key)))))