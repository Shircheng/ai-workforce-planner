# Setup Instructions

This guide is for running AI Workforce Planner locally with Rancher Desktop and Docker Compose.

## 1. Prerequisites

Install these first:

- Git
- Rancher Desktop with Docker support enabled
- Access to the GitLab project
- Your own OpenAI API key

You do not need to install MongoDB locally. Docker Compose starts MongoDB for the project.

## 2. Clone the Project

```powershell
git clone git@gitlab-ssh.endava.com:musangking/ai-workforce-planner.git
cd ai-workforce-planner
```

## 3. Configure Local Environment Variables

Create your local environment file from the safe template:

```powershell
Copy-Item .env.example .env
```

Open `.env` and put your own OpenAI API key:

```env
OPENAI_API_KEY=your-own-openai-api-key
OPENAI_MODEL=gpt-5.4-mini
```

Do not commit `.env`. It is ignored by Git because it contains local secrets.

The backend reads these values through Docker Compose:

```properties
openai.api-key=${OPENAI_API_KEY:}
openai.model=${OPENAI_MODEL:gpt-5.4-mini}
```

If `OPENAI_API_KEY` is empty, OpenAI parsing and AI-generated recommendation explanations will not work. The opportunity parser can still use the local fallback where available.

## 4. Start Rancher Desktop

Open Rancher Desktop and wait until the container engine is running.

Optional check:

```powershell
docker version
docker compose version
```

## 5. Run the Application

From the project root:

```powershell
docker compose up --build
```

Docker Compose starts:

| Service | URL |
| --- | --- |
| Frontend | `http://localhost:5173` |
| Spring Boot backend | `http://localhost:8080` |
| Python analytics | `http://127.0.0.1:8000` |
| MongoDB | `mongodb://localhost:27017` |

## 6. Load Workforce Data

After the app starts, open:

```txt
http://localhost:5173
```

Go to the Workforce Dashboard and import the workforce dataset if the dashboard is empty.

You can import by using the dashboard upload button, or by calling the backend default import endpoint:

```txt
POST http://localhost:8080/api/import/workforce-dataset
```

## 7. Basic Workflow Check

Use this flow to confirm the app is working:

1. Open Workforce Dashboard and confirm workforce data is loaded.
2. Open Talent Explorer and confirm employees are visible.
3. Open Opportunity Intake and create an opportunity.
4. Click Parse requirements.
5. Click Generate options for recommender.
6. Confirm the app navigates to Recommendations and shows generated options.

## 8. Stop or Reset

Stop running containers:

```powershell
docker compose down
```

Reset MongoDB data and start fresh:

```powershell
docker compose down -v
```

Only use `docker compose down -v` when you are okay deleting the local MongoDB volume.

## Troubleshooting

If Docker Compose cannot find `OPENAI_API_KEY`, create `.env` from the template:

```powershell
Copy-Item .env.example .env
```

If ports are already in use, stop the process using one of these ports:

- `5173`
- `8080`
- `8000`
- `27017`

If recommendation parsing or AI explanations fail, confirm `OPENAI_API_KEY` is set in `.env`, then restart:

```powershell
docker compose down
docker compose up --build
```

If the frontend loads but API requests fail, confirm the backend container is running and healthy in Rancher Desktop.
