(ns tourbillon.infr.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defn load-config [profile]
  (aero/read-config (io/resource "config.edn")
                    {:profile profile}))

(def global :global)
(def web :web)
(def secrets :secrets)
(def postgres :postgres)
(def mongo :mongo)
(def smtp :smtp)
(def twilio :twilio)
(def serializer :serializer)
(def jwt :jwt)
