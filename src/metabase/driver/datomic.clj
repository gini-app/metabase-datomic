(ns metabase.driver.datomic
  (:require [datomic.api :as d]
            [metabase.driver :as driver]
            [metabase.query-processor.store :as qp.store]
            [clojure.tools.logging :as log]
            [metabase.driver.datomic.query-processor :as qp]
            [metabase.driver.datomic.util :as util]))

; don't need this anymore since metabase is already updated
(require 'metabase.driver.datomic.monkey-patch)

(driver/register! :datomic)

(defn user-config
  ([]
   (user-config (qp.store/database)))
  ([mbdb]
   (try
     (let [edn (get-in mbdb [:details :config])]
       (read-string (or edn "{}")))
     (catch Exception e
       (log/error e "Datomic EDN is not configured correctly.")
       {}))))

(defn tx-filter []
  (when-let [form (get (user-config) :tx-filter)]
    (eval form)))

(defn db []
  (let [db (-> (get-in (qp.store/database) [:details :db]) d/connect d/db)]
    (if-let [pred (tx-filter)]
      (d/filter db pred)
      db)))

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

(doseq [[feature] features]
  (defmethod driver/supports? [:datomic feature] [_ _]
    (get features feature)))

(defmethod driver/can-connect? :datomic [_ {db :db}]
  (try
    (d/connect db)
    true
    (catch Exception e
      false)))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-database

(defmethod driver/describe-database :datomic [_ instance]
  (let [table-names
        (-> instance (get-in [:details :db]) d/connect d/db
            (->> (d/q qp/schema-attrs-q)
                 (qp/derive-table-names)))]
    {:tables
     (set
      (for [table-name table-names]
        {:name   table-name
         :schema nil}))}))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-table

(defn column-name [table-name col]
  (if (= (namespace col)
         table-name)
    (name col)
    (util/kw->str col)))

(defn describe-table [mbdb {table-name :name}]
  (let [url         (get-in mbdb [:details :db])
        config      (user-config mbdb)
        db          (d/db (d/connect url))
        cols        (->> db
                         (d/q qp/schema-attrs-q)
                         (qp/table-columns table-name))
        rels        (get-in config [:relationships (keyword table-name)])
        xtra-fields (get-in config [:fields (keyword table-name)])]
    {:name   table-name
     :schema nil

     ;; Fields *must* be a set
     :fields
     (-> #{{:name          "db/id"
            :database-type "db.type/ref"
            :base-type     :type/PK
            :pk?           true}}
         (into (for [[col type] cols
                     :let [mb-type (qp/datomic->metabase-type type)]
                     :when mb-type]
                 {:name          (column-name table-name col)
                  :database-type (util/kw->str type)
                  :base-type     mb-type
                  :special-type  mb-type}))
         (into (for [[rel-name {:keys [_path target]}] rels]
                 {:name          (name rel-name)
                  :database-type "metabase.driver.datomic/path"
                  :base-type     :type/FK
                  :special-type  :type/FK}))
         (into (for [[fname {:keys [type _rule]}] xtra-fields]
                 {:name          (name fname)
                  :database-type "metabase.driver.datomic/computed-field"
                  :base-type     type
                  :special-type  type})))}))

(defmethod driver/describe-table :datomic [_ mbdb table]
  (describe-table mbdb table))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/describe-table-fks

(defn guess-dest-column [db table-names col]
  (let [table? (into #{} table-names)
        attrs (d/q qp/dest-columns-q db col)]
    (or (some->> attrs
                 (map namespace)
                 (remove #{"db"})
                 frequencies
                 (sort-by val)
                 last
                 key)
        (table? (name col)))))

(defn describe-table-fks [mbdb {table-name :name}]
  (let [url    (get-in mbdb [:details :db])
        db     (d/db (d/connect url))
        config (user-config mbdb)
        schema (d/q qp/schema-attrs-q db)
        tables (qp/derive-table-names schema)
        cols   (qp/table-columns table-name schema)
        rels   (get-in config [:relationships (keyword table-name)])]

    (-> #{}
        (into (for [[col type] cols
                    :when      (= type :db.type/ref)
                    :let       [dest (guess-dest-column db tables col)]
                    :when      dest]
                {:fk-column-name   (column-name table-name col)
                 :dest-table       {:name   dest
                                    :schema nil}
                 :dest-column-name "db/id"}))
        (into (for [[rel-name {:keys [path target]}] rels]
                {:fk-column-name (name rel-name)
                 :dest-table {:name (name target)
                              :schema nil}
                 :dest-column-name "db/id"})))))

(defmethod driver/describe-table-fks :datomic [_ mbdb table]
  (describe-table-fks mbdb table))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/mbql->native

(defonce mbql-history (atom ()))
(defonce query-history (atom ()))

(defn db-facade [db]
  (reify qp/DbFacade
    (cardinality-many? [this attrid]
      (= :db.cardinality/many (:db/cardinality (d/entity db attrid))))

    (attr-type [this attrid]
      (get-in
        (d/pull db [{:db/valueType [:db/ident]}] attrid)
        [:db/valueType :db/ident]))

    (entid [this ident]
      (d/entid db ident))

    (entity [this eid]
      (d/entity db eid))))

(defmethod driver/mbql->native :datomic [_ {mbqry :query
                                            settings :settings}]
  (swap! mbql-history conj mbqry)
  (qp/mbql->native mbqry settings (db-facade (db))))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; driver/execute-query

(defn execute-query [{:keys [native query] :as native-query}]
  (let [db      (db)
        dqry    (qp/read-query (:query native))
        results (d/q (dissoc dqry :fields) db (:rules (user-config)))
        ;; Hacking around this is as it's so common in Metabase's automatic
        ;; dashboards. Datomic never returns a count of zero, instead it just
        ;; returns an empty result.
        results (if (and (empty? results)
                         (empty? (:breakout query))
                         (#{[[:count]] [[:sum]]} (:aggregation query)))
                  [[0]]
                  results)]
    (if query
      (qp/result-map-mbql (db-facade db) results dqry query)
      (qp/result-map-native results dqry))))

(defmethod driver/execute-query :datomic [_ native-query]
  (swap! query-history conj native-query)
  (let [result (execute-query native-query)]
    (swap! query-history conj result)
    result))


(comment
  (driver/describe-database
   :datomic
   {:details {:db "datomic:dev://localhost:4334/m13n"}})

  (driver/describe-table
   :datomic
   {:details {:db "datomic:dev://localhost:4334/m13n"}}
   {:name "merchant"})

  (driver/describe-table-fks
   :datomic
   {:details {:db "datomic:dev://localhost:4334/m13n"}}
   {:name "txn"})

  )
