'use strict';

// Dependencies
const generalUtils = require('./utils/generalUtils');
const functions = require('firebase-functions');
const admin = require('firebase-admin');
try {
    admin.initializeApp();
} catch (e) { /* App already initialized */ }

// Global variables
const db = admin.firestore();

// Constant properties
const DISTANCE_OFFSET_IN_DEGREE = 0.0001; // more or less 10 meters but depends on position
const TIME_OFFSET_IN_HOURS = 6;

/**
 * Triggers when a violation report is updated, and starts only if the report has been approved.
 * The changes made by this function may trigger the clusteringMS.
 */
exports.groupingMS = functions.firestore.document('/violationReports/{reportId}').onUpdate(async (change, context) => {
    console.log(`groupingMS started.`);

    await doGroupingIfReportIsApproved(change);

    console.log(`groupingMS ended.`);
    return null;
});

async function doGroupingIfReportIsApproved(change) {
    const reportStatusBefore = change.before.get("reportStatus");
    const reportStatusAfter = change.after.get("reportStatus");

    if(reportStatusBefore === "SUBMITTED" && reportStatusAfter === "APPROVED") {
        console.log(`Recognized report approval.`);
        await doGrouping(change.after);
    } else {
        console.log('Not a report approval.');
    }
}

async function doGrouping(violationReportSnap) {
    // Get data of the new report.
    const licensePlate = violationReportSnap.get("licensePlate");
    const latitude = violationReportSnap.get("latitude");
    const longitude = violationReportSnap.get("longitude");
    const uploadTimestamp = violationReportSnap.get("uploadTimestamp");
    const typeOfViolation = violationReportSnap.get("typeOfViolation");
    const municipality = violationReportSnap.get("municipality");

    // Get group to which the report must be added, null if no group exists.
    const groupDocSnap = await getGroupOfReport(municipality, licensePlate, typeOfViolation, latitude, longitude, uploadTimestamp);

    if(groupDocSnap === null) { // If a group doesn't exist...
        console.log('No groups found. Creating a new group...');
        await createNewGroup(licensePlate, typeOfViolation, uploadTimestamp, latitude, longitude, violationReportSnap.ref.id, municipality);
    } else {
        console.log('A group has been found. Adding report to the group...');
        await addViolationReportToExistingGroup(groupDocSnap, violationReportSnap.ref.id, uploadTimestamp.toDate(), municipality)
    }
}

async function getGroupOfReport(municipality, licensePlate, typeOfViolation, latitude, longitude, uploadTimestamp) {
    // Make query on database for getting the group (same licensePlate, same typeOfViolation, similar location and similar timestamp).
    // Note: Queries with range filters on different fields are not supported by Firestore.
    const querySnapshot = await db.collection("municipalities").doc(municipality).collection("groups")
        .where("licensePlate", "==", licensePlate)
        .where("typeOfViolation", "==", typeOfViolation)
        .where("latitude", ">=", latitude - DISTANCE_OFFSET_IN_DEGREE)
        .where("latitude", "<=", latitude + DISTANCE_OFFSET_IN_DEGREE)
        .get();

    // Since Firestore is limited on queries we need to check separately longitude and timestamp.
    return alsoCheckForLongitudeAndTimestampForQuery(querySnapshot, longitude, uploadTimestamp.toDate());
}

function alsoCheckForLongitudeAndTimestampForQuery(querySnapshot, newLongitude, newUploadDate) {
    for (let groupDocSnap of querySnapshot.docs) {
        const groupLongitude = groupDocSnap.data().longitude;
        const groupFirstDate = groupDocSnap.data().firstTimestamp.toDate();
        const groupLastDate = groupDocSnap.data().lastTimestamp.toDate();

        // Range filter also on longitude and date.
        if(groupLongitude >= newLongitude - DISTANCE_OFFSET_IN_DEGREE && groupLongitude <= newLongitude + DISTANCE_OFFSET_IN_DEGREE) {
            if(newUploadDate >= generalUtils.getNewDateWithAddedHours(groupFirstDate, -TIME_OFFSET_IN_HOURS) && newUploadDate <= generalUtils.getNewDateWithAddedHours(groupLastDate, TIME_OFFSET_IN_HOURS))
                return groupDocSnap; // Found the group.
        }
    }
    return null; // No group has been found.
}

async function createNewGroup(licensePlate, typeOfViolation, uploadTimestamp, latitude, longitude, violationReportId, municipality) {
    // Create group data.
    const newGroup = {
        licensePlate: licensePlate,
        typeOfViolation: typeOfViolation,
        groupStatus: "APPROVED",
        firstTimestamp: uploadTimestamp,
        lastTimestamp: uploadTimestamp,
        latitude: latitude,
        longitude: longitude,
        reports: new Array(violationReportId)
    };

    // Add group to database in path: /municipalities/{municipality}/groups/{group}
    await db.collection("municipalities").doc(municipality).collection("groups").add(newGroup);
}

async function addViolationReportToExistingGroup(groupDocSnap, violationReportId, uploadDate, municipality) {
    // Modify existing group data.
    const groupObject = groupDocSnap.data();
    const groupFirstDate = groupObject.firstTimestamp.toDate();
    const groupLastDate = groupObject.lastTimestamp.toDate();
    groupObject.reports.push(violationReportId); // Add report to the reports array.
    if(uploadDate > groupLastDate) // If the new report is the last, update the lastTimestamp field.
        groupObject.lastTimestamp = uploadDate;
    else if(uploadDate < groupFirstDate) // If the new report is earlier than first report (processed later), update the firstTimestamp field.
        groupObject.firstTimestamp = uploadDate;

    // Update data on the database.
    await db.collection("municipalities").doc(municipality).collection("groups").doc(groupDocSnap.ref.id).set(groupObject);
}
