(ns gini.datomic-harness
  (:require
    [clojure.java.io :as jio]
    [datomic.client.api :as dc]
    [datomic.dev-local :as dl])
  (:import (java.util UUID)))

(defn conj-tx [with-db tx]
  (-> with-db
      (dc/with {:tx-data tx})
      :db-after))

(def test-db-spec
  {:server-type :dev-local
   :storage-dir "/tmp/datomic-dev-local"
   :system      "test-mb-system"
   :db-name     "test-db"})

(defn test-db []
  (let [db-spec test-db-spec
        _ (.mkdirs (jio/file (:storage-dir db-spec)))
        client (dc/client db-spec)]
    (dc/delete-database client db-spec)
    (dc/create-database client db-spec)
    (-> client
        (dc/connect db-spec)
        (dc/with-db)
        (conj-tx []))))

(defn cleanup-db [db-spec]
  (dc/delete-database (dc/client db-spec) db-spec)
  (dl/release-db db-spec))

(comment
  (cleanup-db {:server-type :dev-local
               :storage-dir "/tmp/datomic-dev-local"
               :system      "test-system"
               :db-name     "test-db"}))
