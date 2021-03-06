(ns intowow.data
  (:require [clojure.java.io :as io]
            [clojure.string :as s]))

(defn to-long [s]
  (Long/parseLong s))

(defn line->rating [line]
  (->> (s/split line #"\t")
       (map to-long)
       (zipmap [:user :item :rating])))

(defn load-ratings [file]
  (with-open [rdr (io/reader (io/resource file))]
    (->> (line-seq rdr)
         (map line->rating)
         (into []))))

(defn line->item-tuple [line]
  (let [[id name] (s/split line #"\|")]
    (vector (to-long id) name)))

(defn load-items [path]
  (with-open [rdr (io/reader (io/resource path))]
    (->> (line-seq rdr)
         (map line->item-tuple)
         (into {}))))

(def genre-data (atom []))

(defn get-genre-by-id
  "for every id, return the genre as
   the form [children horror]"
  [id]
  (let [index (dec id)]
    (mapv name (map key (filter #(= (val %) 1) (remove #(= (key %) :id) (get @genre-data index)))))))

(defn line->genre-tuple [line]
  (let [line-list (s/split line #"\|")
        genre (drop 5 line-list)
        id (first line-list)]
    (->> (cons id genre)
         (map to-long)
         (zipmap [:id :unknown :action :adventure :animation :children :comedy :crime :documentary :drama :fantasy :film_noir :horror :musical :mystery :romance :sci_fi :thriller :war :western]))))

(defn load-genres
  "return vector as:
 [{:western 0, :fantasy 0, :children 1, :animation 0, :horror 0, :mystery 0, :unknown 0, :musical 0, :romance 0, :war 0, :drama 0, :sci_fi 0, :documentary 0, :id 1, :comedy 1, :thriller 0, :action 0, :film_noir 0, :crime 1}, ... ] "
  [path]
  (with-open [rdr (io/reader (io/resource path))]
    (->> (line-seq rdr)
         (map line->genre-tuple)
         (into []))))

(defn init-genre!
  "initialize the genre-data as an array of map.
  movie item id 1    ---> array index 0
  movie item id 1682 ---> array index 1681
  "
  []
  (reset! genre-data (load-genres "u.item")))

(defn item->name [file]
  (let [items (load-items file)]
    (fn [{:keys [id]}]
      (get items id))))
