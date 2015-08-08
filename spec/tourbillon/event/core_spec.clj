(ns tourbillon.event.core-spec
  (:require [speclj.core :refer :all]
            [tourbillon.event.core :refer :all]))

(describe "Event record"
  (let [e-immediate (create-event "event-id" :job-id {})
        e-scheduled (create-event "event-id" :job-id 5 {})
        e-recurring (create-event "event-id" :job-id 5 10 {})]
    
    (it "can be immediate"
      (should (is-immediate? e-immediate))
      (should-not (is-immediate? e-scheduled))
      (should-not (is-immediate? e-recurring)))

    (it "can be scheduled for a future time"
      (should (is-future? e-scheduled))
      (should-not (is-future? e-immediate))
      (should-not (is-future? e-recurring)))

    (it "can be recurring"
      (should (is-recurring? e-recurring))
      (should-not (is-recurring? e-immediate))
      (should-not (is-recurring? e-scheduled)))

    (describe "recurring events"
      (it "can get the next recurring interval"
        (let [next-event (next-interval e-recurring)]
          (should= 15 (:start next-event))
          (should= 10 (:interval next-event)))))))
