# Smart Surveillance System for Criminal Face Detection

**Student:** Aniket Faguna (LNCDBTC11036)
**University:** LNCT University, Bhopal (M.P.)
**Guide:** Prof. Praveen Sharma | B.Tech CSE | Session: JAN–JUNE 2026

---

## Project Structure

```
smart-surveillance/
├── pom.xml
├── schema.sql                          ← Run this in MySQL first
└── src/main/
    ├── java/com/surveillance/facedetection/
    │   ├── SmartSurveillanceApplication.java
    │   ├── config/
    │   │   └── SecurityConfig.java
    │   ├── entity/
    │   │   ├── User.java
    │   │   ├── Criminal.java
    │   │   ├── DetectionLog.java
    │   │   └── Alert.java
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   ├── CriminalRepository.java
    │   │   ├── DetectionLogRepository.java
    │   │   └── AlertRepository.java
    │   ├── service/
    │   │   ├── UserService.java
    │   │   ├── CriminalService.java
    │   │   ├── FaceDetectionService.java
    │   │   └── AlertService.java
    │   ├── controller/
    │   │   ├── AuthController.java
    │   │   ├── AdminController.java
    │   │   ├── OfficerController.java
    │   │   └── DetectionController.java
    │   └── dto/
    │       └── UserDetailsImpl.java
    └── resources/
        ├── application.properties
        ├── schema.sql
        ├── haarcascade_frontalface_default.xml  ← Add this (see Step 3)
        ├── static/css/style.css
        └── templates/
            ├── login.html
            ├── fragments/navbar.html
            ├── admin/ (dashboard, criminals, add-criminal, users, add-user, logs)
            ├── officer/ (dashboard, alerts, reports)
            └── detection/ (scan, result)
```

---

## Setup Instructions

### Step 1 — MySQL Database

1. Open MySQL Workbench or MySQL CLI
2. Run the schema file:
   ```sql
   SOURCE path/to/smart-surveillance/src/main/resources/schema.sql;
   ```
3. This creates the `surveillance_db` database with all 4 tables
   and inserts two default users:
   - Admin:   username=`admin`   password=`admin123`
   - Officer: username=`officer1` password=`officer123`

---

### Step 2 — Configure application.properties

Open `src/main/resources/application.properties` and update:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/surveillance_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=YOUR_MYSQL_PASSWORD

app.opencv.native-lib-path=C:/opencv/build/java/x64/opencv_java490.dll
```

---

### Step 3 — Install OpenCV

1. Download OpenCV 4.9.0 from: https://opencv.org/releases/
2. Run the installer. Default path: `C:\opencv\`
3. Install OpenCV JAR into Maven local repository:
   ```
   mvn install:install-file \
     -Dfile=C:/opencv/build/java/opencv-490.jar \
     -DgroupId=org.opencv \
     -DartifactId=opencv \
     -Dversion=4.9.0 \
     -Dpackaging=jar
   ```
4. Copy `haarcascade_frontalface_default.xml` from:
   `C:\opencv\sources\data\haarcascades\haarcascade_frontalface_default.xml`
   → paste into: `src/main/resources/haarcascade_frontalface_default.xml`

---

### Step 4 — Build and Run

Using IntelliJ IDEA:
1. Open the project folder in IntelliJ
2. Wait for Maven to download all dependencies
3. Open `SmartSurveillanceApplication.java`
4. Click the green ▶ Run button

Using Maven CLI:
```bash
mvn clean install
mvn spring-boot:run
```

---

### Step 5 — Access the Application

Open browser: **http://localhost:8080**

| Role           | Username  | Password    | Access                        |
|----------------|-----------|-------------|-------------------------------|
| Admin          | admin     | admin123    | Full access + user management |
| Police Officer | officer1  | officer123  | Alerts + scan + reports       |

---

## Workflow (Synopsis Section 8)

1. User logs in → Spring Security authenticates
2. Camera/image feed uploaded via Scan Face page
3. Spring Boot splits frame and sends to FaceDetectionService
4. OpenCV converts to grayscale (preprocessing)
5. Haar Cascade classifier detects faces
6. Histogram comparison runs against all active criminal images in DB
7. If confidence score ≥ 75% → MATCHED
8. Alert generated → stored in `alerts` table
9. Results displayed on dashboard

---

## Default Login Credentials

```
Admin    → username: admin    | password: admin123
Officer  → username: officer1 | password: officer123
```

To add more users: Login as Admin → Users → Register New User

---

## Technology Stack

| Layer    | Technology                          |
|----------|-------------------------------------|
| Frontend | HTML5, CSS3, JavaScript, Thymeleaf  |
| Backend  | Java, Spring Boot 3.2, Spring MVC   |
| Security | Spring Security (BCrypt, RBAC)      |
| Database | MySQL 8.0+                          |
| CV       | OpenCV 4.9.0 (Haar Cascade)         |
| Build    | Maven                               |
