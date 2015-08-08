(ns tourbillon.storage.event-spec
  (:require [speclj.core :refer :all]
            [com.stuartsierra.component :as component]
            [tourbillon.storage.event :refer :all]
            [tourbillon.event.core :refer [create-event next-interval]]))

(def ^:dynamic *store*)

(def event (create-event "event-id" "job-id" 123 {}))

(describe "Local event store"
  (tags :local)
  (around [it]
          (binding [*store* (.start (new-event-store {:type :local
                                                      :db (atom {})
                                                      :last-check-time 122}))]
            (it)
            (.stop *store*)))

  (it "gets a single event"
      (store-event! *store* event)
      (let [found-events (get-events *store* 123)]
        (should= 1 (count found-events))
        (should= event (first found-events))))

  (it "gets multiple events"
      (let [e1 (create-event "event1" :job-id 123 {})
            e2 (create-event "event2" :job-id 123 {})
            _ (do (store-event! *store* e1)
                  (store-event! *store* e2))
            found-events (get-events *store* 123)]
        (should= 2 (count found-events))
        (should-be (partial every? #{e1 e2}) found-events)))

  (it "does not fetch the same event twice"
    (store-event! *store* event)
      (get-events *store* 123)
      (let [none-expected (get-events *store* 123)]
        (should-be empty? none-expected)))

  (it "does not return any events when none exist at the requested timestamp"
      (should-be empty? (get-events *store* 124)))

  (it "gets events before the requested timestamp if any exist"
      (let [e1 (create-event "event1" :job-id 124 {})
            e2 (create-event "event2" :job-id 123 {})
            _ (do (store-event! *store* e1)
                  (store-event! *store* e2))
            found-events (get-events *store* 124)]
        (should= 2 (count found-events))
        (should-be (partial every? #{e1 e2}) found-events))))
