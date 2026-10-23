# Railway Ticket Booking System

SOAP (WSDL-first) · Spring Boot 3.3 · MongoDB atomic seat reservation · Redis · Stripe · Azure services · React

## Run locally (Windows PowerShell)

```powershell
# 1. dependencies (Redis + Azurite; add `mongo` if not using Atlas)
docker compose up -d redis azurite

# 2. environment
$env:MONGODB_URI = "mongodb+srv://<user>:<password>@<cluster>.mongodb.net/railway?retryWrites=true&w=majority"
$env:STRIPE_SECRET_KEY = "sk_test_xxx"
$env:STRIPE_WEBHOOK_SECRET = "whsec_xxx"     # from `stripe listen`

# 3. backend
mvn clean generate-sources
mvn spring-boot:run
# WSDL: http://localhost:8080/ws/booking?wsdl

# 4. concurrency proof (needs Mongo + Redis)
mvn test -Dtest=OverbookingTest

# 5. Stripe webhook forwarding (separate terminal)
stripe listen --forward-to localhost:8080/webhook/stripe

# 6. frontend (separate terminal)
cd frontend
npm install
npm run dev        # http://localhost:5173
```

Using local Mongo instead of Atlas: `docker compose up -d` (starts mongo too) and leave MONGODB_URI unset.
Set `VITE_STRIPE_PK` in `frontend/.env`. See the guide for Azure deployment.
