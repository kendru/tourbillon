(ns tourbillon.schedule.core-spec
  (:require [speclj.core :refer :all]
            [com.stuartsierra.component :as component]
            [tourbillon.schedule.core :refer :all]
            [tourbillon.event.core :refer [create-event next-interval]]
            [tourbillon.storage.object :refer [new-object-store]]
            [tourbillon.storage.event :refer [new-event-store get-events]]
            [tourbillon.domain :refer [Job Event]]
            [overtone.at-at :refer [mk-pool]]))

;; TODO: This ns may need some mocking to test at a unit level, since it acts
;; as a coordinating component

(def the-time 5)
(def e-immediate (create-event "event-id" :job-id {}))
(def e-future (create-event "event-id" :job-id 10 {}))
(def e-recurring (create-event "event-id" :job-id 10 5 {}))

(def ^:dynamic *job-store*)
(def ^:dynamic *event-store*)
(def ^:dynamic *stub-scheduler*)

(describe "Scheduler"
  (around [it]
    (let [job-store (new-object-store {:type :local
                                       :db (atom {})
                                       :schema Job})
          event-store (new-event-store {:type :local
                                        :db (atom {})
                                        :last-check-time the-time})]
      (binding [tourbillon.utils/get-time (constantly the-time)
                *job-store* (.start job-store)
                *event-store* (.start event-store)]
        (binding [*stub-scheduler* {:job-store *job-store*
                                    :event-store *event-store*
                                    :subscriber-system (reify
                                                         tourbillon.workflow.subscribers/SubscriberSystem
                                                         (notify-all! [_ _ _]))}]
          (it)
          (.stop *event-store*)
          (.stop *job-store*)))))

  (describe "sending events"
    
    ;; TODO: Figure out how to determine whether an event was emitted
    (it "enqueues an immediate event without delay")

    (it "saves a future event in the event store"
      (send-event! *stub-scheduler* e-future)
      (should= [e-future] (get-events *event-store* (:start e-future))))

    (it "saves a recurring event in the event store"
      (send-event! *stub-scheduler* e-recurring)
      (should= [e-recurring] (get-events *event-store* (:start e-recurring)))))

  ;; We need some way to mock the emitting of events to effectively test
  (describe "processing events"
    (it "does nothing when no events are returned")

    (it "enqueues all events returned")

    (it "re-enqueues recurring events for specified interval")))
