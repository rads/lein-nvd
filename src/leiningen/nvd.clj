;; The MIT License (MIT)
;;
;; Copyright (c) 2016 Richard Hull
;;
;; Permission is hereby granted, free of charge, to any person obtaining a copy
;; of this software and associated documentation files (the "Software"), to deal
;; in the Software without restriction, including without limitation the rights
;; to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
;; copies of the Software, and to permit persons to whom the Software is
;; furnished to do so, subject to the following conditions:
;;
;; The above copyright notice and this permission notice shall be included in all
;; copies or substantial portions of the Software.
;;
;; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
;; IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
;; FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
;; AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
;; LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
;; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
;; SOFTWARE.

(ns leiningen.nvd
  (:require
    [clojure.java.io :as io]
    [leiningen.run :as run]
    [leiningen.core.main :as main])
  (:import
    [org.slf4j.impl StaticLoggerBinder]
    [org.owasp.dependencycheck Engine]
    [org.owasp.dependencycheck.utils Settings Settings$KEYS]))

; Call this before Dependency Check Core starts logging anything -
; this way, all SLF4J messages from core end up coming through this tasks logger
;(.setTask (StaticLoggerBinder/getSingleton) this)

(defn- populate-settings! [settings]
  (let [mappings {
          Settings$KEYS/PROXY_SERVER [:proxy :server]
          Settings$KEYS/PROXY_PORT [:proxy :port]
          Settings$KEYS/PROXY_USERNAME [:proxy :user]
          Settings$KEYS/PROXY_PASSWORD [:proxy :password]
          Settings$KEYS/CONNECTION_TIMEOUT [:database :connection-timeout]
          Settings$KEYS/DB_DRIVER_NAME [:database :driver-name]
          Settings$KEYS/DB_DRIVER_PATH [:database :driver-path]
          Settings$KEYS/DB_CONNECTION_STRING [:database :connection-string]
          Settings$KEYS/DB_USER [:database :user]
          Settings$KEYS/DB_PASSWORD [:database :password]
          Settings$KEYS/CVE_MODIFIED_12_URL [:cve :url-1.2-modified]
          Settings$KEYS/CVE_MODIFIED_20_URL[:cve :url-2.0-modified]
          Settings$KEYS/CVE_SCHEMA_1_2 [:cve :url-1.2-base]
          Settings$KEYS/CVE_SCHEMA_2_0 [:cve :url-2.0-base]}]
    (Settings/initialize)
    (when-let [cve-valid-for-hours (get-in settings [:cve :valid-for-hours])]
      (Settings/setInt Settings$KEYS/CVE_CHECK_VALID_FOR_HOURS cve-valid-for-hours))
    (if-let [data-directory (get-in settings [:data-directory])]
      (Settings/setString Settings$KEYS/DATA_DIRECTORY data-directory)
      (Settings/setString Settings$KEYS/DATA_DIRECTORY (str (System/getProperty "user.home") "/.lein/.nvd")))
    (doseq [[prop path] mappings]
      (Settings/setStringIfNotEmpty prop (str (get-in settings path))))))

(defn- create-engine []
  (Engine. (.getClassLoader (class populate-settings!))))


(defn update-database!
  "Download the latest data from the National Vulnerability Database
  (NVD) and store a copy in the local database."

  []
  (let [engine (create-engine)]
    (.doUpdates engine)))

(defn purge-database!
  []
  (let [db (io/file (Settings/getDataDirectory) "dc.h2.db")]
    (when (.exists db)
      (.delete db)
      (main/info "Database file purged; local copy of the NVD has been removed"))))

(defn check
  ; TODO
  [])

(defn nvd
  "Scan project dependencies and report known vulnerabilities."
  [project & args]
  (populate-settings! (:nvd project))
  (try
    (condp = (first args)
      "check" (check)
      "purge" (purge-database!)
      "update" (update-database!)
      (main/warn "No such subtask:" (first args)))
    (finally
      (Settings/cleanup true))))
