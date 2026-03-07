(ns glass.db.datascript.sqlite
  (:require
   [datascript.core :as d]
   [datascript.storage.sql.core :as storage-sql])
  (:import
   [org.sqlite SQLiteDataSource]))

(defn init
  [path schema]
  (let [ds (doto (SQLiteDataSource.) (.setUrl (str "jdbc:sqlite:" path)))
        storage (storage-sql/make ds {:dbtype :sqlite})
        conn (d/create-conn schema {:storage storage})]
    conn))

(defn db
  [conn]
  (deref conn))

