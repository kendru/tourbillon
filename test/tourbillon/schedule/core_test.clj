(ns tourbillon.schedule.core-test
  (:require [clojure.test :refer :all]
            [tourbillon.schedule.core :refer :all]
            [tourbillon.event.core :refer [create-event next-interval]]
            [com.stuartsierra.component :as component]))

(defn with-stubbed-time [test-fn]
  (binding [tourbillon.event.store/get-time (constantly 5)]
    (test-fn)))

(use-fixtures :once with-stubbed-time)

#_(deftest test-sending-events
  (let [e-immediate (create-event "event-id" :job-id {})
        e-future (create-event "event-id" :job-id 10 {})
        e-recurring (create-event "event-id" :job-id 10 5 {})
        job-store (atom [])
        event-store {:map-atom (atom {})
                     :last-check 0}
        scheduler {:job-store job-store
                   :event-store event-store}]

    (testing "Sending an immediate event enqueues it without delay"
      (send-event! scheduler e-immediate)
      (is (= [e-immediate] @job-store))
      (reset! job-store []))

    (testing "Sending a future event saves it in the event store"
      (send-event! scheduler e-future)
      (is (= e-future (-> event-store
                          :map-atom
                          deref
                          (get 10)
                          first)))
      (update-in event-store [:map-atom] reset! {}))

    (testing "Sending a recurring event saves it in the event store"
      (send-event! scheduler e-recurring)
      (is (= e-recurring (-> event-store
                          :map-atom
                          deref
                          (get 10)
                          first)))
      (update-in event-store [:map-atom] reset! {}))))

#_(deftest test-processing-events
  (let [e-scheduled (create-event "event-id" :job-id 5 {})
        e-recurring (create-event "event-id" :job-id 5 10 {})
        job-store (atom [])
        event-store {:map-atom (atom {})
                     :last-check (atom 4)}
        scheduler {:job-store job-store
                   :event-store event-store}]

    (testing "Does nothing when no events are returned"
      (process-events! scheduler)
      (is (= [] @job-store)))

    (testing "Enqueues all events returned"
      (update-in event-store [:map-atom] swap! assoc 5 [e-scheduled e-recurring])
      (process-events! scheduler)
      (is (= [e-scheduled e-recurring] @job-store))
      (update-in event-store [:map-atom] reset! {}))

    (testing "Requeues recurring events for specified interval"
      (update-in event-store [:map-atom] swap! assoc 5 [e-scheduled e-recurring])
      (process-events! scheduler)
      (is (= [(next-interval e-recurring)] (-> event-store
                              :map-atom
                              deref
                              (get 15))))
      (update-in event-store [:map-atom] reset! {}))))
