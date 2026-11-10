# External Plugin Messaging

Other Plugin Hub plugins can read the local player's collection log from RuneProfile by posting a `PluginMessage` with `EventBus#post`.

Users must opt into this via `External Plugin Requests > Share collection log with plugins` in the RuneProfile settings. It is off by default.

The data comes from the player's RuneProfile (the same source as the `!log` command), so the player needs a profile, and a page reflects their last sync rather than live game state. RuneProfile syncs automatically, including shortly after a new collection log item.

## Request

The `namespace` should be `runeprofile` and the `name` should be `collection-log-request`.

| Field          | Required | Type   | Description                                                                         |
| -------------- | -------- | ------ | ----------------------------------------------------------------------------------- |
| `sourcePlugin` | Y        | String | The human-facing name of the plugin making the request.                             |
| `page`         | Y        | String | A collection log page name or alias, the same as `!log` accepts (e.g. `Vorkath`).   |
| `requestId`    | N        | Object | Any value. It is returned unchanged in the response so you can match the two up.    |

## Response

RuneProfile answers every request with a `PluginMessage` with namespace `runeprofile` and name `collection-log`. It is posted on the client thread, and always after your own `post` call has returned. Its data map is read-only.

| Field       | Type                | Present         | Description                                                    |
| ----------- | ------------------- | --------------- | -------------------------------------------------------------- |
| `success`   | boolean             | always          | Whether the page was loaded.                                   |
| `page`      | String              | always          | The page as you requested it.                                  |
| `requestId` | Object              | if you sent one | Your `requestId`, unchanged.                                   |
| `name`      | String              | success         | The page's canonical name (e.g. `Vorkath` for `vorki`).        |
| `obtained`  | int                 | success         | Number of items on the page with a quantity above 0.           |
| `total`     | int                 | success         | Number of items on the page.                                   |
| `items`     | List of Map         | success         | One `{id: int, name: String, quantity: int}` per item.         |
| `reason`    | String              | failure         | A machine-readable code, see below.                            |
| `error`     | String              | failure         | A message you can show the user.                               |

| `reason`            | Meaning                                                                    |
| ------------------- | -------------------------------------------------------------------------- |
| `DISABLED`          | The user has not enabled `Share collection log with plugins`.              |
| `INVALID_REQUEST`   | `sourcePlugin` or `page` is missing.                                       |
| `NOT_LOGGED_IN`     | The player is not logged in.                                               |
| `PROFILE_NOT_FOUND` | The player has no RuneProfile yet.                                         |
| `PAGE_NOT_FOUND`    | No collection log page matches `page`.                                     |
| `ERROR`             | Anything else, such as a network error. Try again later.                   |

If RuneProfile is not installed or is turned off, nothing answers.

## Change notification

After each profile sync, RuneProfile posts a `PluginMessage` with namespace `runeprofile` and name `collection-log-changed` (empty data). Request your pages once, keep the result, and request them again when you see this message. Please don't request on every frame or game tick.

Results are cached, so repeated requests don't reach the RuneProfile API, but each request still posts a response to every subscriber.

## Example

The example assumes you have injected RuneLite's event bus: `private @Inject EventBus eventBus;`

```java
// Request
Map<String, Object> data = new HashMap<>();
data.put("sourcePlugin", "My Plugin Name");
data.put("page", "Vorkath");
data.put("requestId", "vorkath-tile");
eventBus.post(new PluginMessage("runeprofile", "collection-log-request", data));

// Response and change notification
@Subscribe
public void onPluginMessage(PluginMessage message) {
    if (!"runeprofile".equals(message.getNamespace())) return;

    if ("collection-log-changed".equals(message.getName())) {
        // request your pages again
        return;
    }

    if (!"collection-log".equals(message.getName())) return;

    Map<String, Object> data = message.getData();
    if (!"vorkath-tile".equals(data.get("requestId"))) return;

    if (Boolean.TRUE.equals(data.get("success"))) {
        String text = data.get("name") + " " + data.get("obtained") + "/" + data.get("total");
    } else if ("DISABLED".equals(data.get("reason"))) {
        // ask the user to enable sharing in the RuneProfile settings
    } else {
        String text = (String) data.get("error");
    }
}
```
