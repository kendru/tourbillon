(ns tourbillon.schedule.core-spec
  (:require [speclj.core :refer :all]
            [com.stuartsierra.component :as component]
            [tourbillon.schedule.core :refer :all]
            [tourbillon.event.core :refer [create-event next-interval]]))

;; TODO: This ns may need some mocking to test at a unit level, since it acts
;; as a coordinating component


