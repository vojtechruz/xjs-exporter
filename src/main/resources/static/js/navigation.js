/**
 * Navigation panel collapsible sections functionality
 */
document.addEventListener('DOMContentLoaded', function() {
    // Get all collapsible section headers
    const collapsibleHeaders = document.querySelectorAll('.collapsible-header');
    
    // Initialize sections based on stored state
    initializeCollapsibleSections();
    
    // Add click event listeners to all collapsible headers
    collapsibleHeaders.forEach(header => {
        header.addEventListener('click', function() {
            // Toggle the collapsed class on the parent section
            const section = this.parentElement;
            section.classList.toggle('collapsed');
            
            // Store the current state in localStorage
            saveCollapsibleState();
        });
    });
    
    /**
     * Initialize collapsible sections based on stored state
     */
    function initializeCollapsibleSections() {
        // Get stored state from localStorage
        const storedState = localStorage.getItem('navigationCollapsibleState');
        
        if (storedState) {
            try {
                const state = JSON.parse(storedState);
                
                // Apply stored state to sections
                collapsibleHeaders.forEach(header => {
                    const section = header.parentElement;
                    const sectionId = section.getAttribute('data-section-id');
                    
                    // If section should be collapsed according to stored state
                    if (state[sectionId] === true) {
                        section.classList.add('collapsed');
                    } else {
                        section.classList.remove('collapsed');
                    }
                });
            } catch (e) {
                console.error('Error parsing stored navigation state:', e);
                // If there's an error, clear the stored state
                localStorage.removeItem('navigationCollapsibleState');
            }
        }
    }
    
    /**
     * Save the current collapsible state to localStorage
     */
    function saveCollapsibleState() {
        const state = {};
        
        // Get the state of all collapsible sections
        collapsibleHeaders.forEach(header => {
            const section = header.parentElement;
            const sectionId = section.getAttribute('data-section-id');
            state[sectionId] = section.classList.contains('collapsed');
        });
        
        // Save to localStorage
        localStorage.setItem('navigationCollapsibleState', JSON.stringify(state));
    }
});