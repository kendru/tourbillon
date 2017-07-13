(ns tourbillon.storage.encryption
  (:require [environ.core :refer [env]]
            [cheshire.core :as json]
            [buddy.core.crypto :as crypto]
            [buddy.core.codecs :as codecs]
            [buddy.core.codecs.base64 :as base64]))


(def secret
  (base64/decode (get env :data-secret)))


(defn serialize [data iv]
  (-> data
      (json/generate-string)
      (codecs/str->bytes)
      (crypto/encrypt secret iv {:algorithm :aes256-gcm})))


(defn deserialize [data iv]
  (-> data
      (crypto/decrypt secret iv {:algorithm :aes256-gcm})
      (codecs/bytes->str)
      (json/parse-string true)))
