package com.example.vtsdaily

object DemoData {

    // Trips shown in ACTIVE view when in demo mode
    fun demoActive(): List<Passenger> = listOf(
        Passenger(
            name = "John Smith",
            id = "DEMO01",
            pickupAddress = "101 Oak St",
            dropoffAddress = "220 Maple Ave",
            typeTime = "08:10",
            phone = "8055551001"
        ),
        Passenger(
            name = "Linda Johnson",
            id = "DEMO02",
            pickupAddress = "334 Palm Dr",
            dropoffAddress = "789 Harbor Blvd",
            typeTime = "08:45",
            phone = "8055551002"
        ),
        Passenger(
            name = "Robert Martinez",
            id = "DEMO03",
            pickupAddress = "452 Seaward Ave",
            dropoffAddress = "910 Beach Dr",
            typeTime = "09:20",
            phone = "8055551003"
        ),
        Passenger(
            name = "Patricia Davis",
            id = "DEMO04",
            pickupAddress = "600 Hilltop Rd",
            dropoffAddress = "305 Pierpont Blvd",
            typeTime = "10:05",
            phone = "8055551004"
        ),
        Passenger(
            name = "Daniel Thompson",
            id = "DEMO05",
            pickupAddress = "1150 Loma Vista Rd",
            dropoffAddress = "420 Anacapa St",
            typeTime = "10:30",
            phone = "8055551005"
        ),
        Passenger(
            name = "Maria Lopez",
            id = "DEMO06",
            pickupAddress = "230 Evergreen Ln",
            dropoffAddress = "1580 Vineyard Ave",
            typeTime = "11:15",
            phone = "8055551006"
        ),
        Passenger(
            name = "Charles Anderson",
            id = "DEMO07",
            pickupAddress = "7150 Citrus St",
            dropoffAddress = "990 Foothill Rd",
            typeTime = "12:40",
            phone = "8055551007"
        ),
        Passenger(
            name = "Susan Clark",
            id = "DEMO08",
            pickupAddress = "880 Miramar Pl",
            dropoffAddress = "1320 Terrace Way",
            typeTime = "13:05",
            phone = "8055551008"
        ),
        Passenger(
            name = "Kevin Nguyen",
            id = "DEMO09",
            pickupAddress = "1400 Encino Ave",
            dropoffAddress = "2750 Sycamore St",
            typeTime = "14:20",
            phone = "8055551009"
        ),
        Passenger(
            name = "Jessica Wilson",
            id = "DEMO10",
            pickupAddress = "3010 Balboa St",
            dropoffAddress = "4100 Sunset Ln",
            typeTime = "15:10",
            phone = "8055551010"
        )
    )

    // Shown in COMPLETED view in demo mode
    fun demoCompleted(date: String): List<CompletedTrip> = listOf(
        CompletedTrip(
            name = "Michael Brown",
            pickupAddress = "500 Victoria Ave",
            dropoffAddress = "230 Beach Dr",
            typeTime = "07:20",
            date = date,
            completedAt = "07:55",
            phone = "8055551011"
        )
    )

    // Shown in REMOVED view in demo mode so you can demo reinstatement
    fun demoRemoved(date: String): List<RemovedTrip> = listOf(
        RemovedTrip(
            name = "Demo Removed Trip",
            pickupAddress = "3900 Market St",
            dropoffAddress = "1225 Juniper Ct",
            typeTime = "09:50",
            date = date,
            reason = TripRemovalReason.REMOVED,
            phone = "8055551020"
        )
    )
}
