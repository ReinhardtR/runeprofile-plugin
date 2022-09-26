<p align="center">  
  <a href="https://runeprofile.com">  
      <p align="center">  
        <img src="https://raw.githubusercontent.com/ReinhardtR/runeprofile-plugin/master/icon.png" width="150" height="150" alt="Logo" />  
		</p>  
	</a>  
	<h1 align="center">
    <b>RuneProfile</b>
  </h1>
  <a href="#"></a>  
	<p align="center">  
    A place to share your OSRS achievements.  
    <br />  
    <a href="https://runeprofile.com"><strong>runeprofile.com</strong></a> 
    <p align="center">
      <img src="https://img.shields.io/endpoint?url=https://i.pluginhub.info/shields/installs/plugin/runeprofile" >
      <img src="https://img.shields.io/endpoint?url=https://i.pluginhub.info/shields/rank/plugin/runeprofile">
      <img src="https://img.shields.io/github/license/ReinhardtR/runeprofile-plugin">
    </p>
	</p>
  <br />
</p>

RuneProfile aims to be a place to share your OSRS achievements with the community,
or just your friends.

The official Hiscores only shows a subset of your achievements and can be tough to navigate.

RuneProfile tries to display all of your important achievements and mimic the in-game UI
to create a familiar user experience.

This plugin is needed to upload your account data to RuneProfile, which will be displayed on the RuneProfile.com
website.

## Features

### Character Model

Show off your best FashionScape or simply flex your best items on your character model.

<div align="center">
    <img src="https://raw.githubusercontent.com/ReinhardtR/runeprofile-plugin/master/assets/model.gif" alt="Character Model">
</div>

> 📝 Some items have unique or animated textures that won't be displayed correctly.

### Skills, Quests, Achievement Diaries & Combat Achievements

<div align="center">
    <img src="https://raw.githubusercontent.com/ReinhardtR/runeprofile-plugin/master/assets/profile-data.png" alt="Data Components">
</div>

Display your progress in the skills, quests, achievement diaries and combat achievements.

### Collection Log

Have you been spooned or are you dry? The collection log will display it, just like in the game.

<div align="center">
    <img src="https://raw.githubusercontent.com/ReinhardtR/runeprofile-plugin/master/assets/collection-log.png" alt="Collection Log Component" height="360">
</div>

#### Tracking when an item was obtained

Not only does the collection log show what you have obtained, but it also shows when you obtained it.
This includes the date and the kill count(s) that you obtained it at.

> 📝 This feature requires you to open the Collection Log entry and update the account after obtaining the item to
> get the most accurate data.

### Hiscores

Your RuneProfile will also show your Hiscores ranks, and will be updated when you update your profile.

<div align="center">
    <img src="https://raw.githubusercontent.com/ReinhardtR/runeprofile-plugin/master/assets/hiscores.png" alt="Hiscores Component" height="360">
</div>

### Private Profile

If you don't want to share your achievements with the world, you can make your profile private.
Limiting the access to your profile with a randomly generated url that you can share with your friends.

### Description

Are you playing on a snowflake account? Share your unique restrictions in your RuneProfile description.
*Or shamelessly plug your Twitch stream.*

## Guide

### Security

It's not recommended to flex your account on RuneProfile if you haven't protected your account. Please follow the
[official security guide](https://www.runescape.com/oldschool/security) by Jagex before using RuneProfile.

### Installation

Install the plugin from the RuneLite plugin hub.

> 📝 Searching "runeprofile" in the plugin hub should bring up the plugin.

### Initial Setup

1. Login to your account if not already logged in.
2. Open the RuneProfile panel from the right hand side. *(look for the RuneProfile logo)*
3. Press the "Update Account"-button to send your account data to RuneProfile.com. *(runeprofile.com/u/{username})*
4. Your RuneProfile should now be available on the RuneProfile website. *(may take a minute to update)*

### Character Model

To show your character model, put on your favorite outfit and press the "Update Model" button.
> 📝 The "Update Account"-button will not update your model. This is intentional, so you can keep the same model when
> updating your account.

### Updating the Collection Log

Unfortunately, this requires a bit of manual work.

1. Open the Collection Log.
2. Open each tab and each entry to allow the plugin to read the data.
3. Press the "Update Account"-button.

> 📝 The Plugin panel has a "Collection Log"-tab that will help you keep track of what entries you have opened.
> Open a tab in the collection log to see what entries you have missed in that tab.

When you've obtained a new item, open the entry (or entries) that contains the item and press the "Update Account"
button. This will update the entry with the new item and track when you obtained the item.

> 📝 Your RuneProfile will not accurately show when you obtained the items that were obtained before using the
> plugin.

### Making your RuneProfile private

If you don't want to share your profile with everyone, you can make your RuneProfile private.

1. Open the RuneProfile plugin panel.
2. Open the Settings tab.
3. Check the "Private Profile"-checkbox.

Now your RuneProfile is only accessible by the randomly generated url below the checkbox.

> 📝 If your generated url is compromised, you can generate a new one with the "Generate New URL"-button.

### Description

You can add a description that will be displayed on your RuneProfile.

1. Open the RuneProfile plugin panel.
2. Open the Settings tab.
3. Write a description in the text box.
4. Press the "Update Description"-button.

Your description will now be displayed on your RuneProfile.
