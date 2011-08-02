(ns pacl.unpack
  (:use pacl.core)
  (:require [clojure.java.io :as jio] [pacl.core])
  (:import (java.util.zip ZipFile ZipInputStream)
           (java.io ByteArrayOutputStream ByteArrayInputStream)
           (pacl.core Archive)))

(def *rar-exts* #{".rar" ".cbr"})

(defn open-rar [f]
  "Returns an Archive containing entries of {:filename :(delay inputstream) :directory?}
   Rar-archives only support reading from files"
  (let [arch (de.innosystec.unrar.Archive. f)
        fileheaders (take-while identity (repeatedly #(.nextFileHeader arch)))
        to-inputstream (fn [fileheader] (with-open [bo (ByteArrayOutputStream.)]
                                         (.extractFile arch fileheader bo)
                                         (ByteArrayInputStream. (.toByteArray bo))))
        entries (for [fh fileheaders]
                  (make-entry :inputstream (delay (to-inputstream fh))
                              :filename (.getFileNameString fh)
                              :directory? (.isDirectory fh)))]
    
    (make-archive :resource arch :entries entries)))

(defprotocol Unpack
  (open-archive [this]
    "Returns an Archive containing entries of {:filename :(delay inputstream) :comment :directory?}
    Attempts to coerce it's argument into a file or inputstream.
    Currently supports anything clojure.java.io/input-stream supports.
    Note that only File supports random seeking.
    Tries strings as url and then file.
    Rar-archives only support filenames."))

(extend-protocol Unpack
  pacl.core.Archive
  (open-archive [arch] arch)
  
  java.lang.String
  (open-archive [s]
    "Check for rarexts, else guess zip.
     Tries first with URL and then File."
    (if (str-contains? s *rar-exts*)
      (open-rar (jio/file s))
      (try 
        (-> s java.net.URL. jio/input-stream open-archive)
        (catch java.net.MalformedURLException e
          (open-archive (jio/file s))))))

  java.io.File
  (open-archive [f]
    (let [arch (ZipFile. f)
          files (enumeration-seq (.entries arch))
          entries (for [e files]
                    (make-entry :inputstream (-> (.getInputStream arch e) jio/input-stream delay)
                                :filename (.getName e)
                                :directory? (.isDirectory e)
                                :comment (.getComment e)))]

      (make-archive :resource arch :entries entries)))
  
  java.lang.Object
  (open-archive [obj]
    (let [zi (ZipInputStream. (jio/input-stream obj))
          files (take-while identity (repeatedly #(.getNextEntry zi)))
          entries (for [e files]
                    (make-entry :inputstream (delay zi)
                                :filename (.getName e)
                                :directory? (.isDirectory e)
                                :comment (.getComment e)))]
      (make-archive :resource zi :entries entries))))


(defn extract-file
  "Creates directories as needed"
  [{:keys [filename inputstream directory?]} path]
  (let [f (jio/file path filename)]
    (jio/make-parents f)
    (if directory?
      (.mkdirs f)
      (jio/copy @inputstream f))))

(defmulti extract-files
  "([from to])
   Extracts all files and create directories as needed.
   From can be filename, archive or a collection of entries.
   Tries to coerce source to an archive.
   Closes any constructed resources afterwards"
  (fn [from to] (type from)))

(defmethod extract-files
  clojure.lang.IPersistentCollection
  [arch path]
  (doseq [f arch]
    (extract-file f path)))

(defmethod extract-files pacl.core.Archive [arch to]
  (extract-files (:entries arch) to))

(defmethod extract-files String [from to]
  (with-open [arch (open-archive from)]
    (extract-files arch to)))
