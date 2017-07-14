(ns tourbillon.storage.sql-helpers
  (:require [clojure.java.jdbc :as sql]))

(defn select-row [conn tbl id]
  (some-> (sql/query conn
                     [(str "SELECT * FROM " ((sql/quoted \") tbl) " WHERE id = ?") id])
            first))
