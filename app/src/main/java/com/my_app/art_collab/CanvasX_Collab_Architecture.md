# CanvasX — Real-Time Collaboration Architecture
> Firebase Realtime Database + Firebase Storage
> 20 ops/second throttle | Optimistic updates | Spring interpolation

---

## 1. Firebase Storage Structure

Blobs are uploaded **once** and never touched again during editing.
Only the download URL is synced — never the image data itself.

```
firebase-storage/
  canvases/
    {canvasId}/
      layers/
        {layerId}.png        ← original bitmap, uploaded on layer creation
        {layerId}_mask.png   ← mask bitmap, uploaded when mask is applied
      thumbnail.jpg          ← low-res preview, regenerated on export
```

**Rules:**
- Only the canvas owner and invited collaborators can read/write their canvas folder
- Max layer blob size: 8MB (enforce client-side before upload)
- Masks are separate blobs — never baked into the layer bitmap

---

## 2. Firebase Realtime Database Structure

```json
{
  "canvases": {
    "{canvasId}": {

      "meta": {
        "title": "My Canvas",
        "ownerId": "user_abc",
        "width": 1080,
        "height": 1920,
        "createdAt": 1711234567,
        "collaborators": {
          "user_abc": "owner",
          "user_xyz": "editor"
        }
      },

      "layers": {
        "{layerId}": {
          "order": 0,
          "type": "image",
          "blobUrl": "https://firebasestorage.../layer_abc.png",
          "visible": true,
          "opacity": 1.0,
          "blendMode": "normal",
          "transform": {
            "x": 0.0, "y": 0.0,
            "scaleX": 1.0, "scaleY": 1.0,
            "rotation": 0.0
          },
          "effects": {
            "brightness": 0.0,
            "contrast": 0.0,
            "blur": 0.0,
            "saturation": 0.0
          },
          "maskUrl": null
        }
      },

      "ops": {
        "{opId}": {
          "userId": "user_xyz",
          "layerId": "layer_abc",
          "type": "transform",
          "payload": { "x": 120.0, "y": 340.0, "scaleX": 1.2, "scaleY": 1.2 },
          "timestamp": 1711234567890
        }
      },

      "presence": {
        "{userId}": {
          "displayName": "Alice",
          "activeLayerId": "layer_abc",
          "lastSeen": 1711234567890,
          "color": "#FF5733"
        }
      }

    }
  }
}
```

---

## 3. Op Types

Every user action becomes one of these ops. Nothing else is synced in real time.

| Op type | Payload fields | Trigger |
|---|---|---|
| `transform` | x, y, scaleX, scaleY, rotation | drag / pinch / rotate |
| `opacity` | value (0.0–1.0) | opacity slider |
| `blend_mode` | mode (string) | blend mode picker |
| `effect` | param, value | any effect slider |
| `layer_add` | layerId, blobUrl, order, type | new layer created |
| `layer_remove` | layerId | layer deleted |
| `layer_reorder` | layerId, fromIndex, toIndex | layer drag in panel |
| `mask_update` | layerId, maskUrl | mask painted / cleared |
| `visibility` | layerId, visible (bool) | eye toggle |

---

## 4. Client Data Flow

### 4a. User performing an action (optimistic update)

```
Finger moves
    │
    ▼
Update local Room DB immediately   ← UI reacts here, zero lag
    │
    ▼
Throttle gate (50ms window)
    │   if within 50ms → discard, keep latest value in buffer
    │   if 50ms elapsed → proceed
    ▼
Push op to /canvases/{id}/ops      ← background, fire-and-forget
    │
    ▼
On finger lift → always push final position immediately (bypass throttle)
```

### 4b. Collaborator receiving an op

```
Firebase listener fires (new op under /ops)
    │
    ▼
Parse op type
    │
    ├── transform → feed into Animatable spring interpolator
    │               do NOT snap, animate from current → new position
    │
    ├── effect / opacity / blend_mode → apply to layer state,
    │                                   Compose recompose handles the rest
    │
    └── layer_add / layer_remove → update layer list in Room,
                                   trigger canvas recompose
```

---

## 5. Throttle Implementation

```kotlin
class OpThrottle(private val intervalMs: Long = 50L) {

    private var lastSentAt = 0L
    private var pendingOp: LayerOp? = null

    // Call this on every drag event
    fun onOp(op: LayerOp, send: (LayerOp) -> Unit) {
        pendingOp = op
        val now = System.currentTimeMillis()
        if (now - lastSentAt >= intervalMs) {
            flush(send)
        }
    }

    // Always call this on finger lift
    fun onFinalOp(op: LayerOp, send: (LayerOp) -> Unit) {
        pendingOp = op
        flush(send)
    }

    private fun flush(send: (LayerOp) -> Unit) {
        pendingOp?.let {
            send(it)
            lastSentAt = System.currentTimeMillis()
            pendingOp = null
        }
    }
}
```

---

## 6. Spring Interpolation (Collaborator Side)

```kotlin
@Composable
fun RemoteLayerCanvas(layer: LayerState, remoteOp: LayerOp?) {

    val offsetX = remember { Animatable(layer.transform.x) }
    val offsetY = remember { Animatable(layer.transform.y) }
    val scaleX  = remember { Animatable(layer.transform.scaleX) }
    val scaleY  = remember { Animatable(layer.transform.scaleY) }

    val spring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    LaunchedEffect(remoteOp) {
        if (remoteOp?.type == "transform") {
            launch { offsetX.animateTo(remoteOp.payload.x, spring) }
            launch { offsetY.animateTo(remoteOp.payload.y, spring) }
            launch { scaleX.animateTo(remoteOp.payload.scaleX, spring) }
            launch { scaleY.animateTo(remoteOp.payload.scaleY, spring) }
        }
    }

    // Use animated values in your Canvas draw call
    Canvas(modifier = Modifier.fillMaxSize()) {
        withTransform({
            translate(offsetX.value, offsetY.value)
            scale(scaleX.value, scaleY.value)
        }) {
            drawBitmap(layer.bitmap)
        }
    }
}
```

---

## 7. Presence System

Presence tells each user which layer their collaborator is currently editing,
shown as a colored indicator on that layer in the panel.

```kotlin
// Update your own presence every time the active layer changes
fun updatePresence(canvasId: String, userId: String, activeLayerId: String) {
    val ref = database.getReference("canvases/$canvasId/presence/$userId")
    ref.setValue(mapOf(
        "displayName" to currentUser.name,
        "activeLayerId" to activeLayerId,
        "lastSeen" to ServerValue.TIMESTAMP,
        "color" to currentUser.presenceColor
    ))
    // Remove presence when user disconnects
    ref.onDisconnect().removeValue()
}

// Listen for all collaborator presence changes
fun observePresence(canvasId: String): Flow<Map<String, PresenceState>> =
    callbackFlow {
        val ref = database.getReference("canvases/$canvasId/presence")
        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val presence = snapshot.children.associate {
                    it.key!! to it.getValue(PresenceState::class.java)!!
                }
                trySend(presence)
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        })
        awaitClose { ref.removeEventListener(listener) }
    }
```

---

## 8. Op Listener

```kotlin
fun observeOps(canvasId: String, since: Long): Flow<LayerOp> =
    callbackFlow {
        val ref = database
            .getReference("canvases/$canvasId/ops")
            .orderByChild("timestamp")
            .startAt(since.toDouble())  // only ops after we joined

        val listener = ref.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val op = snapshot.getValue(LayerOp::class.java) ?: return
                if (op.userId != currentUserId) {   // ignore our own ops
                    trySend(op)
                }
            }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
            override fun onChildChanged(s: DataSnapshot, p: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, p: String?) {}
        })
        awaitClose { ref.removeEventListener(listener) }
    }
```

---

## 9. Op Log Cleanup

Ops accumulate in the database forever if you don't clean them up.

**Strategy:** After a session ends (all users leave), a Cloud Function fires and:
1. Reads the final state of `/canvases/{id}/layers`
2. Writes a clean snapshot back to `/canvases/{id}/layers`
3. Deletes all ops under `/canvases/{id}/ops`

This means the op log only ever contains ops from the **current active session**,
keeping the database small and reads cheap.

```javascript
// Cloud Function (Node.js) — triggers on presence count dropping to 0
exports.cleanupOps = functions.database
    .ref('/canvases/{canvasId}/presence')
    .onWrite(async (change, context) => {
        const presence = change.after.val();
        if (presence && Object.keys(presence).length > 0) return; // still users present

        const canvasId = context.params.canvasId;
        const opsRef = admin.database().ref(`canvases/${canvasId}/ops`);
        await opsRef.remove();
    });
```

---

## 10. Security Rules

```json
{
  "rules": {
    "canvases": {
      "$canvasId": {

        ".read": "auth != null && (
          data.child('meta/ownerId').val() === auth.uid ||
          data.child('meta/collaborators').child(auth.uid).exists()
        )",

        "ops": {
          "$opId": {
            ".write": "auth != null && (
              root.child('canvases').child($canvasId)
                  .child('meta/collaborators').child(auth.uid).exists()
            )",
            ".validate": "newData.hasChildren(['userId','layerId','type','payload','timestamp'])
                          && newData.child('userId').val() === auth.uid"
          }
        },

        "presence": {
          "$userId": {
            ".write": "auth != null && $userId === auth.uid"
          }
        },

        "layers": {
          ".write": "auth != null && (
            data.parent().child('meta/ownerId').val() === auth.uid ||
            data.parent().child('meta/collaborators').child(auth.uid).val() === 'editor'
          )"
        }

      }
    }
  }
}
```

---

## 11. Summary Flow — Full Session

```
1. User A creates canvas
   → uploads layer blobs to Firebase Storage
   → writes layer metadata + blobUrls to /canvases/{id}/layers
   → writes own presence to /canvases/{id}/presence/{userId}

2. User B joins via invite link
   → fetches /canvases/{id}/layers (layer metadata + blobUrls)
   → downloads blobs from Firebase Storage (one time)
   → attaches listener to /canvases/{id}/ops (startAt: now)
   → attaches listener to /canvases/{id}/presence
   → writes own presence

3. User A drags a layer
   → local Room updates instantly (0ms)
   → op throttled to 20/sec → pushed to /canvases/{id}/ops
   → User B listener fires → spring interpolation plays

4. User B adjusts brightness
   → same flow in reverse

5. Both users leave
   → onDisconnect() removes presence entries
   → Cloud Function detects empty presence
   → clears /ops, final layer state remains in /layers

6. Either user re-opens canvas later
   → loads clean state from /layers
   → no ops to replay, starts fresh
```

---

*Architecture version: 1.0 | Matches CanvasX Master Plan v2.0*
