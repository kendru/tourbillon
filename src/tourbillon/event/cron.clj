(ns tourbillon.event.cron
  "Contains fucntions for parsing and handling cron-style specifications."
  (:require [clj-time.core :as t])
  (:import [org.joda.time DateTime]))

(defprotocol IAsStr
  (as-str [this]))

(defprotocol ICanMatch
  (matches? [this n]))

(defprotocol ITypedMatcher
  (match-type [this]))

(defrecord CValue [x]
  IAsStr
  (as-str [_] (str x))
  
  ICanMatch
  (matches? [_ n] (= x n))

  ITypedMatcher
  (match-type [_] ::value))

(defrecord CRange [fst lst]
  IAsStr
  (as-str [_] (str fst "-" lst))

  ICanMatch
  (matches? [_ n] (<= fst n lst))

  ITypedMatcher
  (match-type [_] ::range))

(defrecord CWildcard []
  IAsStr
  (as-str [_] "*")

  ICanMatch
  (matches? [_ _] true)

  ITypedMatcher
  (match-type [_] ::wildcard))

(defrecord CColl [xs]
  IAsStr
  (as-str [_] (clojure.string/join "," xs))

  ICanMatch
  (matches? [_ n] (some #(= n) xs))

  ITypedMatcher
  (match-type [_] ::collection))

(defrecord CLastWeekdayOfMonth [dow]
  IAsStr
  (as-str [_] (str dow "L"))

  ITypedMatcher
  (match-type [_] ::last-weekday-of-month))

(defrecord CLastOfMonth []
  IAsStr
  (as-str [_] "L")

  ITypedMatcher
  (match-type [_] ::last-of-month))

(defrecord CNearWeekday [day]
  IAsStr
  (as-str [_] (str day "W"))

  ITypedMatcher
  (match-type [_] ::near-weekday))

(defrecord CWeekOfMonth [dow wom]
  IAsStr
  (as-str [_] (str dow "#" wom))

  ITypedMatcher
  (match-type [_] ::week-of-month))

(defrecord CStep [x step]
  IAsStr
  (as-str [_] (str (as-str x) "/" step))

  ICanMatch
  (matches? [_ n]
    (cond
     (= ::wildcard (match-type x)) (= 0 (mod n step))
     (= ::range (match-type x))    (and (>= n (:fst x))
                                  (= 0 (mod (- n (:fst x)) step)))
     :else                   false))

  ITypedMatcher
  (match-type [_] ::step))

(defrecord Cron [sec minute hour dom month dow]
  IAsStr
  (as-str [_]
    (apply str (interpose " " (map as-str [sec minute hour dom month dow])))))

(defrecord CronField [parsers input-coercer min max])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Misc conversion functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ^String get-dow-num
  "Convert a string representation of a day of the week (e.g. \"MON\") to its numeric version"
  [^String dow]
  (if (re-matches #"\d" dow)
    dow
    ({"sun" "0"
      "mon" "1"
      "tue" "2"
      "wed" "3"
      "thur" "4"
      "thu" "4"
      "fri" "5"
      "sat" "6"} (clojure.string/lower-case dow))))

(defn ^String get-month-num
  "Coerce a month that may be represented as a string (e.g. \"JAN\") to its numeric version."
  [^String m]
  (if (re-matches #"\d+" m)
    m
    ({"jan" "1"
      "feb" "2"
      "mar" "3"
      "apr" "4"
      "may" "5"
      "jun" "6"
      "jul" "7"
      "aug" "8"
      "sep" "9"
      "oct" "10"
      "nov" "11"
      "dec" "12"} (clojure.string/lower-case m))))

(defn- ^Integer coerce-int [n]
  (if (string? n)
    (Integer/parseInt n)
    (int n)))

(defn validate-input
  "Detemine if input passes the validator. Validator may be a regex, predicate function, or nil.
  If validator is nil, validation always passes. Returns the input if valid and nil if invalid."
  [^String input validator]
  (cond
    (fn? validator) (when (validator input) input)
    (instance? java.util.regex.Pattern validator) (when (re-matches validator input) input)
    (nil? validator) input
    :else nil))
 
(defn coerce-input [input field]
  (if-let [coerce (:input-coercer field)]
    (coerce input)
    input))

(defn test-pattern [input pattern]
  (when (re-matches pattern input)
    input))

(defn test-range [input min max]
  (when (<= min input max)
    input))

(declare extract-parser parse-one)

(defmulti parse* (fn [type _ _] type))

(defmethod parse* :wildcard
  [_ input _]
    (when (= input "*")
      (->CWildcard)))

(defmethod parse* :value
  [_ input f]
  (when-let [value (some-> input
                     (coerce-input f)
                     (test-pattern #"\d+")
                     coerce-int
                     (test-range (:min f) (:max f)))]
    (->CValue value)))

(defmethod parse* :coll
  [_ input f]
  (let [items (clojure.string/split input #",")]
    (when (> (count items) 1)
      (let [items (mapv #(some-> %
                                 (coerce-input f)
                                 (test-pattern #"\d+")
                                 coerce-int
                                 (test-range (:min f) (:max f)))
                        items)]
        (when (not-any? nil? items)
          (->CColl items))))))

(defmethod parse* :range
  [_ input f]
  (when-let [matches (re-matches #"^([^-/]+)-([^-/]+)$" input)]
    (let [[fst lst] (map #(some-> %
                                  (coerce-input f)
                                  (test-pattern #"\d+")
                                  coerce-int
                                  (test-range (:min f) (:max f)))
                         (drop 1 matches))]
      (when (and fst lst)
        (->CRange fst lst)))))

(defmethod parse* :step
  [_ input f]
  (when-let [[_ base step] (re-matches #"^([^/]+)\/([^/]+)$" (str input))]
    (let [base (parse-one (assoc f :parsers [:wildcard :range]) base)
          step (some-> step
                       (coerce-input f)
                       (test-pattern #"\d+")
                       coerce-int
                       (test-range (max 1 (:min f)) (:max f)))]
      (when (and base step)
        (->CStep base step)))))

(defmethod parse* :last-weekday-of-month
  [_ input f]
  (when-let [[_ n] (re-matches #"^(\d+)[Ll]$" input)]
    (when-let [n (some-> n coerce-int (test-range 0 6))]
      (->CLastWeekdayOfMonth n))))

(defmethod parse* :last-of-month
  [_ input f]
  (when (or (= "L" input) (= "l" input))
    (->CLastOfMonth)))

(defmethod parse* :weekday
  [_ input f]
  (when-let [[_ n] (re-matches #"^(\d+)[Ww]$" input)]
    (when-let [n (some-> n coerce-int (test-range (:min f) (:max f)))]
      (->CNearWeekday n))))

(defmethod parse* :week-of-month
  [_ input f]
  (when-let [[_ dow wom] (re-matches #"^(\d)\#(\d)$" input)]
    (let [dow (some-> dow coerce-int (test-range 0 6))
          wom (some-> wom coerce-int (test-range 1 5))]
      (when (and dow wom)
        (->CWeekOfMonth dow wom)))))

(def f-second
  (map->CronField
    {:parsers [:wildcard :step :range :coll :value]
     :min 0
     :max 59}))

(def f-minute
  (map->CronField {:parsers [:wildcard :step :range :coll :value]
                   :min 0
                   :max 59}))

(def f-hour
  (map->CronField {:parsers [:wildcard :step :range :coll :value]
                   :min 0
                   :max 23}))

(def f-dom
  (map->CronField {:parsers [:wildcard :last-of-month :weekday :step :coll :range :value]
                   :min 1
                   :max 31}))

(def f-month
  (map->CronField {:parsers [:wildcard :step :range :coll :value]
                   :min 1
                   :max 12
                   :input-coercer get-month-num}))

(def f-dow
  (map->CronField {:parsers [:wildcard :step :range :coll :last-weekday-of-month :week-of-month :value]
                   :min 0
                   :max 6
                   :input-coercer get-dow-num}))

(defn- extract-parser [field]
  (fn [parser-kw]
    (fn [input]
      (parse* parser-kw input field))))

(defn parse-one
  "Parse an input given a field's specification"
  [field input]
  ((apply some-fn (map (extract-parser field) (:parsers field)))
   input))

(defn parse-all
  "Takes a number of pairs of input, CronField and returns a seq of each input parsed
   with the specified field's parsing rules."
  [& args]
  (for [[input field] (partition 2 args)]
    (parse-one field input)))

(defn parse-cron* [[sec minute hour dom month dow]]
  (let [parts (parse-all
                sec f-second
                minute f-minute
                hour f-hour
                dom f-dom
                month f-month
                dow f-dow)]
    (when (not-any? nil? parts)
      (apply ->Cron parts))))

(defn parse-cron [input]
  (let [[& parts] (clojure.string/split input #" ")]
    (condp = (count parts)
      5 (parse-cron* (into ["0"] parts))
      6 (parse-cron* (into [] parts))
      nil)))

(defn is-leap-year?
  "Determine if the given year is a leap year.
  Credit: http://stackoverflow.com/questions/22951049/check-leap-years-with-clojure#answer-22951086"
  [^Integer year]
  (and (zero? (bit-and year 3))
       (or (not (zero? (mod year 25)))
           (zero? (bit-and year 15)))))

(def day-of-week
  (let [ts [0 3 2 5 0 3 5 1 4 6 2 4]]
    (memoize
      (fn [^Integer y ^Integer m ^Integer d]
        (let [y (if (< m 3) (dec y) y)]
          (int (mod (Math/floor (+ y (/ y 4.0) (/ y -100) (/ y 400) (get ts (dec m)) d)) 7)))))))

(defn get-days-in-month [^Integer month ^Integer year]
  (if (and (= month 2) (is-leap-year? year))
    29
    (get [31 28 31 30 31 30 31 31 30 31 30 31] (dec month))))

(defn- get-dow-matcher [spec]
  (cond
    (satisfies? ICanMatch spec)          #(matches? spec (first %))
    (= ::last-weekday-of-month (match-type spec))
    (fn [[day month year]]
      (and (= (:dow spec)
              (day-of-week year month day))
           (> (+ day 7) (get-days-in-month month year))))
    (= ::week-of-month (match-type spec))
    (fn [day month year]
      (let [dow (:dow spec)
            wom (:wom spec)
            starts-on-dow (day-of-week year month 1)
            first-dow-day (inc (if (>= dow starts-on-dow)
                                 (- dow starts-on-dow)
                                 (- (+ dow 7) starts-on-dow)))]
        (= day (+ first-dow-day (* (inc wom) 7)))))
    ;; Should never be reached
    :else                                (constantly true)))

(defn- get-dom-matcher [spec]
  (cond
    (satisfies? ICanMatch spec)   #(matches? spec (first %))
    (= ::last-of-month (match-type spec))
    (fn [[day month year]]
      (= day (get-days-in-month month year)))
    ;; Matches the nearest weekday to :day specified in spec.
    ;; If the current "day" passed in is a weekday and matches the day in the spec,
    ;; it passes. Otherwise, It matches the nearest weekday within the month. E.g. if
    ;; the specification was 1W, and the first of the month was a Saturday, it would match
    ;; the 3rd of the month, not the last day of the previous month.   
    (= ::near-weekday (match-type spec))
    (fn [[day month year]]
      (let [day-near (:day spec)
            dow (day-of-week year month day)]
        (or (and (= day day-near)
                 (< 0 dow < 6))
            (and (= dow 1)
                 (or (= day (inc day-near))
                     (and (= day 3)
                          (= day-near 1))))
            (and (= dow 5)
                 (or (= day (dec day-near))
                     (let [last-of-month (get-days-in-month month year)]
                       (and (= day (- last-of-month 2))
                            (= day-near last-of-month))))))))
    ;; Should never be reached
    :else                         (constantly true)))

(defn- get-day-matcher
  [cron]
  (let [matches-dow? (get-dow-matcher (:dow cron))
        matches-dom? (get-dom-matcher (:dom cron))]
    (cond
     (every? #(= ::wildcard (match-type %)) [(:dom cron) (:dow cron)]) (constantly true)
     (= ::wildcard (match-type (:dom cron))) matches-dow?
     (= ::wildcard (match-type (:dow cron))) matches-dom?
     :else (fn [date] (or (matches-dow? date) (matches-dom? date))))))

(defn get-next-time [^DateTime now cron]
  (loop [second (t/second now)
         minute (t/minute now)         
         hour (t/hour now)
         day (t/day now)
         month (t/month now)
         year (t/year now)]
    (if (matches? (:month cron) month)
      (if ((get-day-matcher cron) [day month year])
        (if (matches? (:hour cron) hour)
          (if (matches? (:minute cron) minute)
            (if (matches? (:sec cron) second)
              (t/date-time year month day hour minute second) ; Base case
              (if (= 59 second)
                (if (= 59 minute)
                  (if (= 23 hour)
                    (if (= (get-days-in-month month year) day)
                      (if (= 12 month)
                        (recur 0 0 0 1 1 (inc year))
                        (recur 0 0 0 1 (inc month) year))
                      (recur 0 0 0 (inc day) month year))
                    (recur 0 0 (inc hour) day month year))
                  (recur 0 (inc minute) hour day month year))
                (recur (inc second) minute hour day month year)))
            (if (= 59 minute)
              (if (= 23 hour)
                (if (= (get-days-in-month month year) day)
                  (if (= 12 month)
                    (recur 0 0 0 1 1 (inc year))
                    (recur 0 0 0 1 (inc month) year))
                  (recur 0 0 0 (inc day) month year))
                (recur 0 0 (inc hour) day month year))
              (recur 0 (inc minute) hour day month year)))
          (if (= 23 hour)
            (if (= (get-days-in-month month year) day)
              (if (= 12 month)
                (recur 0 0 0 1 1 (inc year))
                (recur 0 0 0 1 (inc month) year))
              (recur 0 0 0 (inc day) month year))
            (recur 0 0 (inc hour) day month year)))
        (if (= (get-days-in-month month year) day)
          (if (= 12 month)
            (recur 0 0 0 1 1 (inc year))
            (recur 0 0 0 1 (inc month) year))
          (recur 0 0 0 (inc day) month year)))
      (if (= 12 month)
        (recur 0 0 0 1 1 (inc year))
        (recur 0 0 0 1 (inc month) year)))))

