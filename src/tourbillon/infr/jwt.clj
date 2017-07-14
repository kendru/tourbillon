(ns tourbillon.infr.jwt
  (:require [com.stuartsierra.component :as component]
            [buddy.sign.jwt :as jwt]
            [taoensso.timbre :as log]))

(defprotocol IJwtSigner
  (sign [this claims])
  (unsign [this claims]))

(defrecord JwtSigner [secret]
  component/Lifecycle
  (start [component]
    (log/info "Starting JWT message signer")
    component)

  (stop [component]
    (log/info "Stopping JWT message signer")
    component)

  IJwtSigner
  (sign [this claims]
    (jwt/sign claims secret {:alg :hs256}))

  (unsign [this claims]
    (jwt/unsign claims secret {:alg :hs256})))

(defn make-signer [config]
  (map->JwtSigner {:config config}))
