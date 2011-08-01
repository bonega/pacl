(ns pacl.pack
  (:use [clojure.contrib.import-static])
  (:require [clojure.java.io :as jio])
  (:import (java.util.zip ZipOutputStream ZipEntry ZipException)))

(import-static java.util.zip.ZipOutputStream DEFLATED STORED )

(defn- fix-dir-name [filename]
  (if (and (.isDirectory (jio/file filename)) (not= \/ (last filename)))
    (str filename "/")
    filename))

(defn- list-files [path]
  (let [f (jio/file path)]
    (if (.isDirectory f)
      (apply vector f (map list-files (.listFiles f)))
      [f])))

(defmulti compress
  "([source target & {:keys [method comment]}])
   Compress source entries to a target that can be coerced into an OutputStream.
   Can take an optional map. See java.util.zip.ZipEntry for details."
  (fn [source target & opts] (type source)))

(defmethod compress clojure.lang.IPersistentCollection
  [source target & {:keys [comment method]}]
  (let [out (jio/output-stream target)
        zout (ZipOutputStream. out)]
    (doto zout
      (.setLevel (or method DEFLATED))
      (.setComment (or comment "")))
    
    (doseq [{:keys [filename comment inputstream directory?]} source
            :let [e (ZipEntry. (fix-dir-name filename))]]
      (when comment
        (.setComment e comment))
      (.putNextEntry zout e)
      (when-not directory?
        (jio/copy @inputstream zout)))
    (try (.close zout)
         (catch ZipException err
           (.close out)
           (throw err)))))

(defmethod compress pacl.core.Archive
  [source target & opts]
  (apply compress (:entries source) target opts))


(defn- relative-path [root file]
  (.. (jio/file root) toURI (relativize (.toURI (jio/file file))) toString))

(defn- make-relative-entry [root file]
  (let [f (jio/file file)
        filename (relative-path root file)]
    (if (.isDirectory f)
      (pacl.core/make-entry :filename filename :directory? true)
      (pacl.core/make-entry :filename filename :inputstream (delay (jio/input-stream f))))))

(defn files->entries
  "Constructs entries from one or more filenames.
   Every file or directory should be in the same branch.
   Works recursively"
  [& files]
  (let [root (or (-> files first jio/file .getParent) "")
        filelist (mapcat (comp flatten list-files jio/file) files)]
    (map (partial make-relative-entry root) filelist)))

(defn compress-files [files to & opts]
  (apply compress (apply files->entries files) to opts))


