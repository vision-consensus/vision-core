How to Build
Prepare dependencies
JDK 1.8 (JDK 1.9+ are not supported yet)
On Linux Ubuntu system (e.g. Ubuntu 16.04.4 LTS), ensure that the machine has Oracle JDK 8, instead of having Open JDK 8 in the system. If you are building the source code by using Open JDK 8, you will get Build Failed result.
Open UDP ports for connection to the network
MINIMUM 2 CPU Cores
Build and Deploy automatically using scripts
Please take a look at the Vision Deployment Scripts repository.
Getting the code with git
Use Git from the console, see the Setting up Git and Fork a Repo articles.
develop branch: the newest code
master branch: more stable than develop. In the shell command, type:
git clone https://github.com/vision-consensus/visi-core.git
git checkout -t origin/master
For Mac, you can also install GitHub for Mac then fork and clone our repository.

If you'd rather not use Git, Download the ZIP

Building from source code
Building using the console:

cd vision-core
./gradlew build
Building using IntelliJ IDEA (community version is enough):

Please run ./gradlew build once to build the protocol files

Start IntelliJ. Select File -> Open, then locate to the vision-core folder which you have git cloned to your local drive. Then click Open button on the right bottom.
Check on Use auto-import on the Import Project from Gradle dialog. Select JDK 1.8 in the Gradle JVM option. Then click OK.
IntelliJ will import the project and start gradle syncing, which will take several minutes, depending on your network connection and your IntelliJ configuration
Enable Annotations, Preferences -> Search annotations -> check Enable Annotation Processing.
When the syncing finishes, select Gradle -> Tasks -> build, and then double click build option.
