(ns tourbillon.infr.serializer
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [cheshire.core :as json]
            [buddy.core.crypto :as crypto]
            [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs.base64 :as base64]))


(defprotocol ISerializer
  (serialize [this data])
  (deserialize [this data]))


(defrecord EncryptedSerializer [secret-str]
  component/Lifecycle
  (start [component]
    (log/info "Starting encrypted serializer")
    (assoc component :secret (base64/decode secret-str)))

  (stop [component]
    (log/info "Stopping encrypted serializer")
    (assoc component :secret nil))
  
  ISerializer
  (serialize [this data]
    (let [{:keys [secret]} this
          iv (nonce/random-bytes 12)
          encrypted (-> data
                        (json/generate-string)
                        (codecs/str->bytes)
                        (crypto/encrypt secret iv {:algorithm :aes256-gcm}))]
      (byte-array (mapcat seq [iv encrypted]))))

  (deserialize [this serialized]
    (let [{:keys [secret]} this
          iv (byte-array (take 12 serialized))
          data (byte-array (drop 12 serialized))]
      (-> data
          (crypto/decrypt secret iv {:algorithm :aes256-gcm})
          (codecs/bytes->str)
          (json/parse-string true)))))

          
(defmulti make-serializer :type)

(defmethod make-serializer :encrypted
  [{:keys [secret]}]
  (map->EncryptedSerializer {:secret-str secret}))


