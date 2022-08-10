# Example

On log out
- Get collection log state
- Get player stats state
- Get boss kc
- etc.
- Send to API endpoint

# DB
- Instead of sending to website api then to DB.
- Just send straight to the DB.

# Model
- RuneLite model exporter creates a .obj file with the player model.
- Send the player model to a database.
- Display the 3d model with three.js.

# Collection Log
- Collection Log export plugin

# Quest Progress

# Boss KC

# Player Stats

# Advanced Stats
- EHP
- Time to Max

# Other
- Maybe make a default page for all players on the hiscores.
- Use Firebase for storing .obj files.
- Use PlanetScale for user data.
- Plugin uploads .obj file to Firebase.
- If possible: plugin uploads data to PlanetScale. (Then Next.js revalidates static site.)
- Else: plugin makes a post request to the API, and the api uploads the data to PlanetScale.
