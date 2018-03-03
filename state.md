## Application states

### First-run

First run / user signup involves

1. Welcome screen / sign in to Google Account
3. OAuth for SmugMug



### Welcome screen

Shown if we detect the user is not "signed in"

That could be either 
- the cookie has expired
- their google token isn't working..

Welcome to PhotoSync - 
please sign in using Google Account to continue


### User authentication

Application loading

* Browser requests javascript to load

API request

* Client makes request to API endpoint
* invalid or non-existent claim cookie
  * backend returns a 301 - Client app prompts user to authenticate
* valid claim cookie
  * backend 

Frontend starts 
* CRSF (JWE) token is generated including:
  * nonce
  * ip
  * UA 
  * expiry
* Google OAuth Flow begins, includes CRSF token
* Google OAuth Flow returns, returns CRSF token in state
* CRSF token validated
* Google OAuth Access Token stored to access user's Google
* JWE claim cookie issued to identify user to application

Frontend flow

* User loads app
