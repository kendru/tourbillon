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
      )

  (it "does not return any events when none exist at the requested timestamp"
      (should-be empty? (get-events *store* 124))))
