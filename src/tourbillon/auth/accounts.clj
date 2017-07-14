(ns tourbillon.auth.accounts
  (:require [com.stuartsierra.component :as component]
            [buddy.hashers :as hashers]
            [buddy.auth :refer [throw-unauthorized]]
            [buddy.core.nonce :as nonce]
            [buddy.core.codecs :as codecs]
            [buddy.core.codecs.base64 :as base64]
            [clj-time.coerce :as time-coerce]
            [taoensso.timbre :as log]
            [tourbillon.storage.object :as store]
            [tourbillon.auth.core :as auth]
            [tourbillon.infr.jwt :as jwt]))

(defn random-key []
  (codecs/bytes->str
    (base64/encode
      (nonce/random-bytes 21))))

(defn create-api-key!
  ([account-store] (create-api-key! account-store auth/default-permissions))
  ([account-store permissions]
   (let [api-key (random-key)
         secret (random-key)]
     (store/create! account-store {:id api-key
                                   :secret-digest (hashers/encrypt secret)
                                   :permissions permissions})
     (log/info (str "Created API key: " api-key))
     {:api-key api-key :api-secret secret})))

(defn create-session-token [signer account-store api-key secret]
  (let [err-msg "API key or secret invalid"
        details (store/find-by-id account-store api-key)]
    (if details
      (if (hashers/check secret (:secret-digest details))
        (let [claim (auth/get-api-claim details)]
          (log/info (str "Created session token for key: " api-key))
          {:token (jwt/sign signer claim)
           :expires (time-coerce/to-long (:exp claim))})
        (throw-unauthorized err-msg))
      (do (log/warn (str "Could not find API key: " api-key))
          (throw-unauthorized err-msg)))))
