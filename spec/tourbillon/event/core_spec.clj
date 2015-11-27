(ns tourbillon.event.core-spec
  (:require [speclj.core :refer :all]
            [tourbillon.event.core :refer :all]))

(describe "Event record"
  (with e-immediate (create-event "event-id" :job-id {}))
  (with e-scheduled (create-event "event-id" :job-id 5 {}))
  (with e-recurring (create-event "event-id" :job-id 5 10 {}))
  (with e-cron (create-event "event-id" :job-id 5 "* * * * *" {}))
    
  (it "can be immediate"
      (should (is-immediate? @e-immediate))
      (should-not (is-immediate? @e-scheduled))
      (should-not (is-immediate? @e-recurring))
      (should-not (is-immediate? @e-cron)))

  (it "can be scheduled for a future time"
      (should (is-future? @e-scheduled))
      (should-not (is-future? @e-immediate))
      (should-not (is-future? @e-recurring))
      (should-not (is-future? @e-cron)))

  (it "can be recurring"
      (should (is-recurring? @e-recurring))
      (should (is-recurring? @e-cron))
      (should-not (is-recurring? @e-immediate))
      (should-not (is-recurring? @e-scheduled)))

  (describe "recurring events"
            (it "can get the next recurring interval"
                (let [next-event (next-interval @e-recurring)]
                  (should= 15 (:start next-event))
                  (should= 10 (:interval next-event))))))
