FROM python:3.12-slim-bookworm
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 U2NET_HOME=/models \
    OMP_NUM_THREADS=2 NUMBA_NUM_THREADS=2 UPLOADS_ROOT=/app/uploads
COPY requirements*.txt ./
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir -r requirements.txt
COPY pipeline.py ./
COPY app app
COPY assets assets
COPY tests tests
RUN python -m unittest discover -s tests
HEALTHCHECK --interval=20s --timeout=5s --start-period=120s --retries=5 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8090/health')"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8090", "--workers", "1", "--limit-concurrency", "8"]
