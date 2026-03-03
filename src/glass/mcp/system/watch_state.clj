(ns glass.mcp.system.watch-state
  (:require
   [glass.mcp.protocol :as mcp]
   [glass.mcp.state :as state]))

(defn watch
  [_k _r old-state new-state]
  (when (not= (:prompts old-state) (:prompts new-state))
    (doseq [session-id (keys (:sessions new-state))]
      (mcp/notify {:state state/state
                   :session-id session-id
                   :method "notifications/prompts/list_changed"})))
  (when (not= (:resources old-state) (:resources new-state))
    (doseq [session-id (keys (:sessions new-state))]
      (mcp/notify {:state state/state
                   :session-id session-id
                   :method "notifications/resources/list_changed"})))
  (when (not= (:tools old-state) (:tools new-state))
    (doseq [session-id (keys (:sessions new-state))]
      (mcp/notify {:state state/state
                   :session-id session-id
                   :method "notifications/tools/list_changed"}))))

(defn start!
  [_opts]
  (add-watch state/state ::notify-changes watch))
