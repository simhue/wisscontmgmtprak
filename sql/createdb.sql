-- Database: ContMgmt

-- DROP DATABASE "ContMgmt";

CREATE DATABASE "ContMgmt"
    WITH 
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'German_Germany.1252'
    LC_CTYPE = 'German_Germany.1252'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

ALTER DATABASE "ContMgmt"
    SET search_path TO '"$user", public, topology, tiger';

-- Table: public.route

-- DROP TABLE public.route;

CREATE TABLE public.route
(
    id integer NOT NULL DEFAULT nextval('route_id_seq'::regclass),
    description text COLLATE pg_catalog."default",
    CONSTRAINT route_pkey PRIMARY KEY (id)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.route
    OWNER to postgres;

-- Table: public.points

-- DROP TABLE public.points;

CREATE TABLE public.points
(
    id integer NOT NULL DEFAULT nextval('points_id_seq'::regclass),
    point geometry,
    CONSTRAINT points_pkey PRIMARY KEY (id)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.points
    OWNER to postgres;

-- Table: public.routepoints

-- DROP TABLE public.routepoints;

CREATE TABLE public.routepoints
(
    pointid integer NOT NULL DEFAULT nextval('routepoints_pointid_seq'::regclass),
    routeid integer NOT NULL DEFAULT nextval('routepoints_routeid_seq'::regclass),
    CONSTRAINT routepoints_pkey PRIMARY KEY (pointid, routeid)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.routepoints
    OWNER to postgres;