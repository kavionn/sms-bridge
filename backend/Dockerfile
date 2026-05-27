FROM node:20

# Tambahkan arg agar cache invalid
ARG CACHEBUST=1

WORKDIR /app

# Clone selalu fresh
RUN --mount=type=secret,id=GITHUB_REPO,required=true \
    git clone $(cat /run/secrets/GITHUB_REPO) /app

RUN npm install

RUN mkdir -p /app/data && chown -R node:node /app

VOLUME /app/data

USER node

EXPOSE 7860

CMD ["npm", "start"]
