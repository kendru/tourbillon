(ns tourbillon.event.store-test
  (:require [clojure.test :refer :all]
            [tourbillon.event.core :refer :all]
            [tourbillon.event.store :refer :all]))

(def event (create-event "event-id" :job-id 123 {}))

(def ^:dynamic *store*)

(defn with-store [test-fn]
  (let [store (new-store (atom {}) 122)]
    (.start store)
    (binding [*store* store]
      (test-fn))
    (.stop store)))

(use-fixtures :each with-store)

(deftest test-getting-single-event
  (testing "Gets a single event at the correct timestamp"
    (store-event! *store* event)
    (let [found-events (get-events *store* 123)]
      (is (= 1 (count found-events)))
      (is (= event (first found-events)))
      (is (empty? (get-events *store* 124))))))

(deftest test-getting-multiple-events
  (let [event2 (create-event "event2" :job-id 123 {})]
    (testing "Retrieves multiple events at a single timestamp"
      (store-event! *store* event)
      (store-event! *store* event2)
      (let [found-events (get-events *store* 123)]
        (is (= 2 (count found-events)))
        (is (every? #{event event2} found-events))))))

;; Because we cannot guarantee that the scheduler will never skip a specific timestamp
;; (e.g. check 1ms before a timestamp and again after 1002 ms), we need to retrieve all
;; events up through the timestamp specified
(deftest test-getting-past-events
  (let [event2 (create-event "event2" :job-id 124 {})
        event3 (create-event "event3" :job-id 123 {})]
    (testing "Concatenates all events up through given timestamp"
      (store-event! *store* event)
      (store-event! *store* event2)
      (store-event! *store* event3)
      (let [found-events (get-events *store* 125)]
        (is (= 3 (count found-events)))
        (is (every? #{event event2 event3} found-events))))))

(deftest test-removing-events
  (testing "Removes events once they are taken out"
    (store-event! *store* event)
    (get-events *store* 123) ; should take the events out
    (let [found-events (get-events *store* 123)]
      (is (empty? found-events)))))
