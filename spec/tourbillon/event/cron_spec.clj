(ns tourbillon.event.cron-spec
  (:require [speclj.core :refer :all]
            [tourbillon.event.cron :refer :all]
            [clj-time.core :as t])
  (:import [org.joda.time DateTime]))

(describe "Cron-style parsing and handling"
  (it "Defines a Cron record"
    (let [second (->CValue 30)
          minute (->CValue 0)
          hour (->CRange 0 6)
          day-of-month (->CValue 1)
          month (->CStep "*" 2)
          day-of-week (->CWildcard)
          cron (->Cron second minute hour day-of-month month day-of-week)]
      (should= second (:sec cron))
      (should= minute (:minute cron))
      (should= hour (:hour cron))
      (should= day-of-month (:dom cron))
      (should= month (:month cron))
      (should= day-of-week (:dow cron))))

  (describe "Cron entry string generation"
    (it "generates a value itself"
      (should= "5" (as-str (->CValue 5))))

    (it "generates a range of values"
      (should= "5-10" (as-str (->CRange 5 10))))

    (it "generates a wildcard"
      (should= "*" (as-str (->CWildcard))))

    (it "modifies another value with a step"
      (should= "*/15" (as-str (->CStep (->CWildcard) 15)))
      (should= "0-30/5" (as-str (->CStep (->CRange 0 30) 5))))

    (it "generates a last day modifier"
      (should= "5L" (as-str (->CLastWeekdayOfMonth 5))))

    (it "generates a near weekday modifier"
      (should= "15W" (as-str (->CNearWeekday 15))))

    (it "generates a week of month modifier"
      (should= "1#2" (as-str (->CWeekOfMonth 1 2))))

    (it "generates an entire cron spec"
      (let [second (->CValue 30)
            minute (->CStep (->CRange 0 30) 5)
            hour (->CColl [3 6 9])
            day-of-month (->CValue 1)
            month (->CWildcard)
            day-of-week (->CWildcard)
            cron (->Cron second minute hour day-of-month month day-of-week)]
        (should= "30 0-30/5 3,6,9 1 * *" (as-str cron)))))

  (describe "parsing input"
    (it "parses a simple entry with seconds"
      (let [cron (parse-cron "* * * * * *")]
        (should-be (partial every? #(= (->CWildcard) %))
          ((juxt :sec :minute :hour :dom :month :dow) cron))))

    (it "parses a simple entry without seconds"
      (let [cron (parse-cron "* * * * *")]
        (should-be (partial every? #(= (->CWildcard) %))
                   ((juxt :minute :hour :dom :month :dow) cron))
        (should= (->CValue 0) (:sec cron))))

    (it "parses a cron with more complex values"
      (let [cron (parse-cron "0 */15 0,2,4,6 * * 0#1")]
        (should= (->CValue 0) (:sec cron))
        (should= (->CStep (->CWildcard) 15) (:minute cron))
        (should= (->CColl [0 2 4 6]) (:hour cron))
        (should= (->CWildcard) (:dom cron))
        (should= (->CWildcard) (:month cron))
        (should= (->CWeekOfMonth 0 1) (:dow cron))))

    (describe "seconds/minutes"
      (it "handles wildcards"
        (should= (->CWildcard) (parse-one f-second "*")))

      (it "handles simple values"
        (should= (->CValue 5) (parse-one f-second "5")))

      (it "rejects out of range values"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-second) ["-1" "60" "999" "foo"])))

      (it "handles collections of values"
        (should= (->CColl [2 4 6]) (parse-one f-second "2,4,6")))

      (it "rejects out of range collections"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-second) ["-1,5,6" "59,60,61" "3,foo" "foo,bar"])))

      (it "handles ranges of values"
        (should= (->CRange 0 59) (parse-one f-second "0-59")))

      (it "rejects out of range ranges"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-second) ["-1--10" "0-60" "70-100" "foo-bar"])))

      (it "handles steps of wildcards"
        (should= (->CStep (->CWildcard) 15) (parse-one f-second "*/15")))

      (it "handles steps of ranges"
        (should= (->CStep (->CRange 15 45) 15) (parse-one f-second "15-45/15")))

      (it "rejects out of range steps"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-second) ["*/0" "*/61" "15-45/100" "*/bar"])))

      (it "rejects last values"
        (should-be nil? (parse-one f-second "5L"))))

    (describe "hours"
      (it "handles wildcards"
        (should= (->CWildcard) (parse-one f-hour "*")))

      (it "handles simple values"
        (should= (->CValue 5) (parse-one f-hour "5")))

      (it "rejects out of range values"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-hour) ["-1" "24" "999" "foo"])))

      (it "handles collections of values"
        (should= (->CColl [2 4 6]) (parse-one f-hour "2,4,6")))

      (it "rejects out of range collections"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-hour) ["-1,5,6" "23,24" "3,foo" "foo,bar"])))

      (it "handles ranges of values"
        (should= (->CRange 0 23) (parse-one f-hour "0-23")))

      (it "rejects out of range ranges"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-hour) ["-1--10" "0-24" "70-100" "foo-bar"])))

      (it "handles steps of wildcards"
        (should= (->CStep (->CWildcard) 4) (parse-one f-hour "*/4")))

      (it "handles steps of ranges"
        (should= (->CStep (->CRange 12 23) 3) (parse-one f-hour "12-23/3")))

      (it "rejects out of range steps"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-hour) ["*/0" "*/25" "3-9/100" "*/bar"])))

      (it "rejects last values"
        (should-be nil? (parse-one f-hour "5L"))))

    (describe "days of month"
      (it "handles wildcards"
        (should= (->CWildcard) (parse-one f-dom "*")))

      (it "handles simple values"
        (should= (->CValue 1) (parse-one f-dom "1")))

      (it "rejects out of range values"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dom) ["-1" "0" "32" "foo"])))

      (it "handles collections of values"
        (should= (->CColl [1 15 17]) (parse-one f-dom "1,15,17")))

      (it "rejects out of range collections"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dom) ["-1,1" "0,2" "31,32" "12,foo" "foo,bar"])))

      (it "handles ranges of values"
        (should= (->CRange 1 15) (parse-one f-dom "1-15")))

      (it "rejects out of range ranges"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dom) ["-1-1" "0-2" "31-40" "12-foo" "foo-bar"])))

      (it "handles steps of wildcards"
        (should= (->CStep (->CWildcard) 5) (parse-one f-dom "*/5")))

      (it "handles steps of ranges"
        (should= (->CStep (->CRange 15 30) 5) (parse-one f-dom "15-30/5")))

      (it "handles last days"
        (should= (->CLastOfMonth) (parse-one f-dom "L")))

      (it "rejects out of range last days"
        (should-be nil? (parse-one f-dom "5L")))

      (it "handles nearest weekday"
        (should= (->CNearWeekday 15) (parse-one f-dom "15W")))

      (it "rejects out or range weekdays"
        (should-be nil? (parse-one f-dom "32W"))))

    (describe "months"
      (it "handles wildcards"
        (should= (->CWildcard) (parse-one f-month "*")))

      (it "handles simple values"
        (should= (->CValue 5) (parse-one f-month "5")))

      (it "handles symbolic month names"
        (should= (->CValue 1) (parse-one f-month "JAN")))

      (it "rejects out of range values"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-month) ["-1" "13" "999" "foo"])))

      (it "handles collections of values"
        (should= (->CColl [2 4 6]) (parse-one f-month "2,4,6")))

      (it "handles collections of symbolic names"
        (should= (->CColl [2 4 6]) (parse-one f-month "feb,apr,jun")))

      (it "rejects out of range collections"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-month) ["-1,5,6" "12,13" "3,foo" "foo,bar"])))

      (it "handles ranges of values"
        (should= (->CRange 1 12) (parse-one f-month "1-12")))

      (it "handles ranges of symolic names"
        (should= (->CRange 1 12) (parse-one f-month "JAN-DEC")))

      (it "rejects out of range ranges"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-month) ["-1--10" "0-13" "70-100" "foo-bar"])))

      (it "handles steps of wildcards"
        (should= (->CStep (->CWildcard) 4) (parse-one f-month "*/4")))

      (it "handles steps of ranges"
        (should= (->CStep (->CRange 1 4) 2) (parse-one f-month "1-4/2")))

      (it "handles steps of symbolic ranges"
        (should= (->CStep (->CRange 1 4) 2) (parse-one f-month "Jan-Apr/2")))

      (it "rejects out of range steps"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-month) ["*/0" "*/13" "3-9/100" "*/bar"])))

      (it "rejects last values"
        (should-be nil? (parse-one f-month "5L"))))

    (describe "day of week"
      (it "handles wildcards"
        (should= (->CWildcard) (parse-one f-dow "*")))

      (it "handles simple values"
        (should= (->CValue 5) (parse-one f-dow "5")))

      (it "handles symbolic day names"
        (should= (->CValue 1) (parse-one f-dow "MON")))

      (it "rejects out of range values"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dow) ["-1" "7" "999" "foo"])))

      (it "handles collections of values"
        (should= (->CColl [2 4 6]) (parse-one f-dow "2,4,6")))

      (it "handles collections of symbolic names"
        (should= (->CColl [2 4 6]) (parse-one f-dow "tue,thu,sat")))

      (it "rejects out of range collections"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dow) ["-1,5,6" "6,7" "3,foo" "foo,bar"])))

      (it "handles ranges of values"
        (should= (->CRange 0 6) (parse-one f-dow "0-6")))

      (it "handles ranges of symolic names"
        (should= (->CRange 0 6) (parse-one f-dow "SUN-SAT")))

      (it "rejects out of range ranges"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dow) ["-1--10" "0-7" "70-100" "foo-bar"])))

      (it "handles steps of wildcards"
        (should= (->CStep (->CWildcard) 2) (parse-one f-dow "*/2")))

      (it "handles steps of ranges"
        (should= (->CStep (->CRange 1 5) 2) (parse-one f-dow "1-5/2")))

      (it "handles steps of symbolic ranges"
        (should= (->CStep (->CRange 1 5) 2) (parse-one f-dow "Mon-Fri/2")))

      (it "rejects out of range steps"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dow) ["*/0" "*/8" "3-6/100" "*/bar"])))

      (it "handles last days"
        (should= (->CLastWeekdayOfMonth 6) (parse-one f-dow "6L")))

      (it "rejects out of range last days"
        (should-be nil? (parse-one f-dow "12L")))

      (it "handles week of month"
        (should= (->CWeekOfMonth 1 2) (parse-one f-dow "1#2")))

      (it "rejects out of range weeks of month"
        (should-be (partial every? nil?)
                   (map (partial parse-one f-dow) ["7#2" "1#6" "foo#bar" "3#foo" "foo#3"]))))

    (describe "parsing entire cron"
      (it "should reject a cron if any field is bad"
        (should-be (partial every? nil?)
          (map parse-cron ["*"
                           "* * * * * * *"
                           "foo foo foo foo foo foo"
                           "-1 * * * * *"
                           "* 60 * * * *"
                           "* * 25 * * *"
                           "* * * 32 * *"
                           "* * * * 13 *"
                           "* * * * * 7"])))))

  (describe "finding execution times"
    (with now (t/date-time 2015 1 2 12 0 0)) ; Jan 2, 2015, 12:00 PM

    (it "should find when the execution time matches now"
        (should= @now
                 (get-next-time @now (parse-cron "* * * * * *"))))

    (it "should find an execution time at the first instant of next month"
        (should= (t/date-time 2015 2 1 0 0 0)
                 (get-next-time @now (parse-cron "* * * 1 * *"))))

    (it "should find an execution time at the first instant of next year"
        (should= (t/date-time 2016 1 1 0 0 0)
                 (get-next-time @now (parse-cron "* * * 1 1 *"))))

    (it "should find a time that matches in the same day"
        (should= (t/date-time 2015 1 2 12 30 0)
                 (get-next-time @now (parse-cron "* 30-59/15 * * * *"))))))
