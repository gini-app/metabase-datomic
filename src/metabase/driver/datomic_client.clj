(ns metabase.driver.datomic
  (:require [datomic.client.api :as dc]
            [metabase.driver :as driver]
            [metabase.query-processor.store :as qp.store]

            [metabase.driver.datomic.util :as util]))

(driver/register! :datomic-client)

(defn db
  ([] (db (get (qp.store/database) :details)))
  ([db-spec]
   (-> db-spec
       (merge {:server-type :peer-server
               :validate-hostnames false})
       (dc/client)
       (dc/connect db-spec)
       (dc/db))))

(def features
  {:basic-aggregations                     true
   :standard-deviation-aggregations        true
   :case-sensitivity-string-filter-options true
   :foreign-keys                           true
   :nested-queries                         true
   :expressions                            false
   :expression-aggregations                false
   :native-parameters                      false
   :binning                                false})

(doseq [[feature supported?] features]
  (defmethod driver/supports? [:datomic feature] [_ _] supported?))

(defmethod driver/can-connect? :datomic-client [_ db-spec]
  (try
    (db db-spec)
    true
    (catch Exception e
      false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-database

(def reserved-prefixes
  #{"fressian"
    "db"
    "db.alter"
    "db.excise"
    "db.install"
    "db.sys"
    "db.attr"
    "db.entity"})

(defn attributes
  "Query db for all attribute entities."
  [db]
  (dc/qseq '{:find [(pull ?e [*])] :where [[?e :db/valueType]]} db))

(defn attrs-by-table
  "Map from table name to collection of attribute entities."
  [db]
  (reduce #(update %1 (namespace (:db/ident %2)) conj %2)
          {}
          (attributes db)))

(defn derive-table-names
  "Find all \"tables\" i.e. all namespace prefixes used in attribute names."
  [db]
  ;; TODO(alan) Use pattern match to remove all datomic reserved prefixes
  (remove reserved-prefixes
          (keys (attrs-by-table db))))

(defmethod driver/describe-database :datomic-client [_ instance]
  (let [db-spec (get instance :details)
        table-names (derive-table-names (db db-spec))]
    {:tables
     (set
      (for [tname table-names]
        {:name   tname
         :schema nil}))}))
